package com.example.slagalica.wsserver;

import com.example.slagalica.Model.Question;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeneralKnowledgeGameState implements GameState {
    private final List<Question> questions;

    private int currentQuestionIndex;
    private int player1Score;
    private int player2Score;
    private String firstAnsweredByUid;
    private Integer selectedAnswer;
    private boolean answerRevealed;
    private boolean finished;

    public GeneralKnowledgeGameState(List<Question> questions) {
        this.questions = questions == null
                ? Collections.emptyList()
                : new ArrayList<>(questions);
        currentQuestionIndex = 0;
        player1Score = 0;
        player2Score = 0;
        answerRevealed = false;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.GENERAL_KNOWLEDGE;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public void setPlayer1Score(int player1Score) {
        this.player1Score = player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public void setPlayer2Score(int player2Score) {
        this.player2Score = player2Score;
    }

    public String getFirstAnsweredByUid() {
        return firstAnsweredByUid;
    }

    public void setFirstAnsweredByUid(String firstAnsweredByUid) {
        this.firstAnsweredByUid = firstAnsweredByUid;
    }

    public Integer getSelectedAnswer() {
        return selectedAnswer;
    }

    public void setSelectedAnswer(Integer selectedAnswer) {
        this.selectedAnswer = selectedAnswer;
    }

    public boolean isAnswerRevealed() {
        return answerRevealed;
    }

    public void setAnswerRevealed(boolean answerRevealed) {
        this.answerRevealed = answerRevealed;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("gameType", getGameType());
        json.put("questions", buildQuestionsJson(questions));
        json.put("currentQuestionIndex", currentQuestionIndex);
        json.put("player1Score", player1Score);
        json.put("player2Score", player2Score);
        json.put("firstAnsweredByUid", firstAnsweredByUid == null ? JSONObject.NULL : firstAnsweredByUid);
        json.put("selectedAnswer", selectedAnswer == null ? JSONObject.NULL : selectedAnswer);
        json.put("answerRevealed", answerRevealed);
        json.put("finished", finished);
        return json;
    }

    public static JSONArray buildQuestionsJson(List<Question> questions) {
        JSONArray questionsJson = new JSONArray();
        if (questions == null) {
            return questionsJson;
        }

        for (Question question : questions) {
            JSONObject questionJson = new JSONObject();
            questionJson.put("question", question.getQuestion());
            questionJson.put("answer1", question.getAnswer1());
            questionJson.put("answer2", question.getAnswer2());
            questionJson.put("answer3", question.getAnswer3());
            questionJson.put("answer4", question.getAnswer4());
            questionJson.put("correctAnswer", question.getCorrectAnswer());
            questionsJson.put(questionJson);
        }
        return questionsJson;
    }
}
