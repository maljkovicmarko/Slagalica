package com.example.slagalica.wsserver;

import com.example.slagalica.Model.Question;

public class GeneralKnowledgeEngine implements GameEngine {
    private static final String ACTION_ANSWER = "general_knowledge_answer";
    private static final long QUESTION_DURATION_MS = 5000L;
    private static final long ANSWER_REVEAL_DURATION_MS = 1000L;
    private static final int CORRECT_ANSWER_POINTS = 10;
    private static final int WRONG_ANSWER_POINTS = -5;

    @Override
    public String getGameType() {
        return GameTypes.GENERAL_KNOWLEDGE;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        if (!ACTION_ANSWER.equals(action.getActionType())) {
            return GameActionResult.rejected("Unsupported General Knowledge action.");
        }
        if (!PhaseTypes.GENERAL_KNOWLEDGE_QUESTION_OPEN.equals(session.getGamePhase().getPhaseType())) {
            return GameActionResult.rejected("General Knowledge answer is not allowed in the current phase.");
        }

        GeneralKnowledgeGameState gameState = requireGeneralKnowledgeState(session);
        if (gameState == null) {
            return GameActionResult.rejected("General Knowledge state is not active.");
        }
        if (gameState.isFinished()) {
            return GameActionResult.rejected("General Knowledge is already finished.");
        }
        if (gameState.getFirstAnsweredByUid() != null) {
            return GameActionResult.rejected("Question already has an accepted answer.");
        }
        if (gameState.getCurrentQuestionIndex() >= gameState.getQuestions().size()) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        int selectedAnswer = action.getData().optInt("answer", 0);
        if (selectedAnswer < 1 || selectedAnswer > 4) {
            return GameActionResult.rejected("Answer must be between 1 and 4.");
        }

        Question question = gameState.getQuestions().get(gameState.getCurrentQuestionIndex());
        int scoreDelta = selectedAnswer == question.getCorrectAnswer()
                ? CORRECT_ANSWER_POINTS
                : WRONG_ANSWER_POINTS;

        applyScoreDelta(session, gameState, playerUid, scoreDelta);
        gameState.setFirstAnsweredByUid(playerUid);
        gameState.setSelectedAnswer(selectedAnswer);
        revealCurrentAnswer(session, gameState);

        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        GeneralKnowledgeGameState gameState = requireGeneralKnowledgeState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        String phaseType = session.getGamePhase().getPhaseType();
        if (PhaseTypes.GENERAL_KNOWLEDGE_QUESTION_OPEN.equals(phaseType)) {
            revealCurrentAnswer(session, gameState);
            return GameActionResult.accepted(true);
        }
        if (PhaseTypes.GENERAL_KNOWLEDGE_ANSWER_REVEAL.equals(phaseType)) {
            moveToNextQuestionOrFinish(session, gameState);
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private GeneralKnowledgeGameState requireGeneralKnowledgeState(SessionState session) {
        if (session.getActiveGameState() instanceof GeneralKnowledgeGameState) {
            return (GeneralKnowledgeGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScoreDelta(SessionState session,
                                 GeneralKnowledgeGameState gameState,
                                 String playerUid,
                                 int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void revealCurrentAnswer(SessionState session, GeneralKnowledgeGameState gameState) {
        gameState.setAnswerRevealed(true);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.startGamePhase(
                PhaseTypes.GENERAL_KNOWLEDGE_ANSWER_REVEAL,
                gameState.getCurrentQuestionIndex() + 1,
                ANSWER_REVEAL_DURATION_MS
        );
    }

    private void moveToNextQuestionOrFinish(SessionState session, GeneralKnowledgeGameState gameState) {
        int nextQuestionIndex = gameState.getCurrentQuestionIndex() + 1;
        if (nextQuestionIndex >= gameState.getQuestions().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentQuestionIndex(nextQuestionIndex);
        gameState.setFirstAnsweredByUid(null);
        gameState.setSelectedAnswer(null);
        gameState.setAnswerRevealed(false);

        session.setTurnState(TurnState.both(
                session.getPlayer1Uid(),
                session.getPlayer1Uid(),
                session.getPlayer1Uid(),
                session.getPlayer2Uid()
        ));
        session.startGamePhase(
                PhaseTypes.GENERAL_KNOWLEDGE_QUESTION_OPEN,
                nextQuestionIndex + 1,
                QUESTION_DURATION_MS
        );
    }

    private void finishGame(SessionState session, GeneralKnowledgeGameState gameState) {
        gameState.setFinished(true);
        gameState.setFirstAnsweredByUid(null);
        gameState.setSelectedAnswer(null);
        gameState.setAnswerRevealed(false);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.setGamePhase(null);
    }
}
