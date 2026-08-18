package com.example.slagalica.wsserver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GameSequence {
    private static final List<String> ORDERED_GAME_TYPES = Collections.unmodifiableList(Arrays.asList(
            GameTypes.GENERAL_KNOWLEDGE,
            GameTypes.CONNECTIONS,
            GameTypes.ASSOCIATIONS,
            GameTypes.GUESS_THE_COMBINATION,
            GameTypes.STEP_BY_STEP,
            GameTypes.FIND_THE_NUMBER
    ));

    private GameSequence() {
    }

    public static List<String> orderedGameTypes() {
        return ORDERED_GAME_TYPES;
    }

    public static int indexOf(String gameType) {
        if (gameType == null) {
            return -1;
        }
        return ORDERED_GAME_TYPES.indexOf(gameType);
    }

    public static String nextAfter(String gameType) {
        int currentIndex = indexOf(gameType);
        int nextIndex = currentIndex + 1;
        if (currentIndex < 0 || nextIndex >= ORDERED_GAME_TYPES.size()) {
            return null;
        }
        return ORDERED_GAME_TYPES.get(nextIndex);
    }

    public static boolean isLast(String gameType) {
        int currentIndex = indexOf(gameType);
        return currentIndex >= 0 && currentIndex == ORDERED_GAME_TYPES.size() - 1;
    }
}
