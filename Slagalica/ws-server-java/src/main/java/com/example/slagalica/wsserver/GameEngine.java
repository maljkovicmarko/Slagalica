package com.example.slagalica.wsserver;

public interface GameEngine {
    String getGameType();

    GameActionResult handleAction(SessionState session, String playerUid, GameAction action);

    GameActionResult handleTimeout(SessionState session, long phaseVersion);

    default GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        return GameActionResult.accepted(false);
    }
}
