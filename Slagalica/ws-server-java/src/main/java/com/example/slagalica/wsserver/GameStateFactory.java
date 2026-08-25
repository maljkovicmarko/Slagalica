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
            java.util.List<ConnectionsGameState.RoundState> rounds = session.isChallengeRun()
                    ? connectionsQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid(), session.getChallengeContentSeed())
                    : connectionsQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid());
            ConnectionsGameState gameState = new ConnectionsGameState(session.isChallengeRun()
                    ? java.util.Collections.singletonList(rounds.get(0))
                    : rounds);
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.ASSOCIATIONS.equals(gameType)) {
            java.util.List<AssociationsGameState.RoundState> rounds = session.isChallengeRun()
                    ? associationsQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid(), session.getChallengeContentSeed())
                    : associationsQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid());
            AssociationsGameState gameState = new AssociationsGameState(session.isChallengeRun()
                    ? java.util.Collections.singletonList(rounds.get(0))
                    : rounds);
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.GUESS_THE_COMBINATION.equals(gameType)) {
            java.util.Random random = session.isChallengeRun()
                    ? new java.util.Random(session.getChallengeContentSeed() ^ 0x534b4f43L)
                    : new java.util.Random();
            java.util.List<GuessCombinationGameState.RoundState> rounds = new java.util.ArrayList<>();
            rounds.add(new GuessCombinationGameState.RoundState(session.getPlayer1Uid(), randomCombination(random)));
            if (!session.isChallengeRun()) {
                rounds.add(new GuessCombinationGameState.RoundState(session.getPlayer2Uid(), randomCombination(random)));
            }
            GuessCombinationGameState gameState = new GuessCombinationGameState(rounds);
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.STEP_BY_STEP.equals(gameType)) {
            java.util.List<StepByStepGameState.RoundState> rounds = session.isChallengeRun()
                    ? stepByStepQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid(), session.getChallengeContentSeed())
                    : stepByStepQuestionProvider.selectRounds(session.getPlayer1Uid(), session.getPlayer2Uid());
            StepByStepGameState gameState = new StepByStepGameState(session.isChallengeRun()
                    ? java.util.Collections.singletonList(rounds.get(0))
                    : rounds);
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        if (GameTypes.FIND_THE_NUMBER.equals(gameType)) {
            java.util.Random random = session.isChallengeRun()
                    ? new java.util.Random(session.getChallengeContentSeed() ^ 0x4d4f4a42L)
                    : new java.util.Random();
            java.util.List<FindNumberGameState.RoundState> rounds = new java.util.ArrayList<>();
            rounds.add(randomFindNumberRound(session.getPlayer1Uid(), random));
            if (!session.isChallengeRun()) {
                rounds.add(randomFindNumberRound(session.getPlayer2Uid(), random));
            }
            FindNumberGameState gameState = new FindNumberGameState(rounds);
            gameState.setPlayer1Score(session.getPlayer1Score());
            gameState.setPlayer2Score(session.getPlayer2Score());
            return gameState;
        }
        return null;
    }

    private java.util.List<String> randomCombination(java.util.Random random) {
        java.util.List<String> combination = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            combination.add(GuessCombinationGameState.SYMBOLS.get(random.nextInt(GuessCombinationGameState.SYMBOLS.size())));
        }
        return combination;
    }

    private FindNumberGameState.RoundState randomFindNumberRound(String ownerUid, java.util.Random random) {
        int targetNumber = random.nextInt(900) + 100;
        java.util.List<Integer> numbers = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            numbers.add(random.nextInt(9) + 1);
        }

        java.util.List<Integer> mediumNumbers = java.util.Arrays.asList(10, 15, 20);
        java.util.List<Integer> largeNumbers = java.util.Arrays.asList(25, 50, 75, 100);
        numbers.add(mediumNumbers.get(random.nextInt(mediumNumbers.size())));
        numbers.add(largeNumbers.get(random.nextInt(largeNumbers.size())));
        java.util.Collections.shuffle(numbers, random);
        return new FindNumberGameState.RoundState(ownerUid, targetNumber, numbers);
    }
}
