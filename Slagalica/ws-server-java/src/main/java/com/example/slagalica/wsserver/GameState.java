package com.example.slagalica.wsserver;

import org.json.JSONObject;

public interface GameState {
    String getGameType();

    boolean isFinished();

    JSONObject toJson();
}
