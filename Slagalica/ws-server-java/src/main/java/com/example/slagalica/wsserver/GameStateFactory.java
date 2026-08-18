package com.example.slagalica.wsserver;

public class GameStateFactory {
    private final ConnectionsQuestionProvider connectionsQuestionProvider;
    private final AssociationsQuestionProvider associationsQuestionProvider;
    private final StepByStepQuestionProvider stepByStepQuestionProvider;

    public GameStateFactory() {
        connectionsQuestionProvider = new ConnectionsQuestionProvider();
        associationsQuestionProvider = new AssociationsQuestionProvider();
        stepByStepQuestionProvider = new StepByStepQuestionProvider();
    }

    public GameState create(String gameType, SessionState session) {
        if (GameTypes.CONNECTIONS.equals(gameType)) {
            ConnectionsGameState gameState = new ConnectionsGameState(connectionsQuestionProvider.selectRounds(
                    session.getPlayer1Uid(),
                    session.getPlayer2Uid()
            ));
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.ASSOCIATIONS.equals(gameType)) {
            AssociationsGameState gameState = new AssociationsGameState(associationsQuestionProvider.selectRounds(
                    session.getPlayer1Uid(),
                    session.getPlayer2Uid()
            ));
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.GUESS_THE_COMBINATION.equals(gameType)) {
            GuessCombinationGameState gameState = new GuessCombinationGameState(java.util.Arrays.asList(
                    new GuessCombinationGameState.RoundState(session.getPlayer1Uid(), randomCombination()),
                    new GuessCombinationGameState.RoundState(session.getPlayer2Uid(), randomCombination())
            ));
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.STEP_BY_STEP.equals(gameType)) {
            StepByStepGameState gameState = new StepByStepGameState(stepByStepQuestionProvider.selectRounds(
                    session.getPlayer1Uid(),
                    session.getPlayer2Uid()
            ));
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.FIND_THE_NUMBER.equals(gameType)) {
            FindNumberGameState gameState = new FindNumberGameState(java.util.Arrays.asList(
                    randomFindNumberRound(session.getPlayer1Uid()),
                    randomFindNumberRound(session.getPlayer2Uid())
            ));
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        return null;
    }

    private java.util.List<String> randomCombination() {
        java.util.List<String> combination = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 4; i++) {
            combination.add(GuessCombinationGameState.SYMBOLS.get(random.nextInt(GuessCombinationGameState.SYMBOLS.size())));
        }
        return combination;
    }

    private FindNumberGameState.RoundState randomFindNumberRound(String ownerUid) {
        java.util.Random random = new java.util.Random();
        int targetNumber = random.nextInt(900) + 100;
        java.util.List<Integer> numbers = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            numbers.add(random.nextInt(9) + 1);
        }

        java.util.List<Integer> mediumNumbers = java.util.Arrays.asList(10, 15, 20);
        java.util.List<Integer> largeNumbers = java.util.Arrays.asList(25, 50, 75, 100);
        numbers.add(mediumNumbers.get(random.nextInt(mediumNumbers.size())));
        numbers.add(largeNumbers.get(random.nextInt(largeNumbers.size())));
        java.util.Collections.shuffle(numbers);
        return new FindNumberGameState.RoundState(ownerUid, targetNumber, numbers);
    }
}
