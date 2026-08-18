package com.example.slagalica.wsserver;

import java.util.HashMap;
import java.util.Map;

public class GameEngineRegistry {
    private final Map<String, GameEngine> enginesByGameType;

    public GameEngineRegistry() {
        enginesByGameType = new HashMap<>();
        register(new GeneralKnowledgeEngine());
        register(new ConnectionsEngine());
        register(new AssociationsEngine());
        register(new GuessCombinationEngine());
        register(new StepByStepEngine());
        register(new FindNumberEngine());
    }

    public GameEngine getEngine(String gameType) {
        if (gameType == null) {
            return null;
        }
        return enginesByGameType.get(gameType);
    }

    private void register(GameEngine engine) {
        enginesByGameType.put(engine.getGameType(), engine);
    }
}
