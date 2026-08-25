package com.example.slagalica.wsserver;

import com.example.slagalica.Model.Question;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionalChallengeRulesTest {
    @Test
    public void winnerShareUsesSeventyFivePercentAndRoundsDown() {
        assertEquals(7, RegionalChallengeRules.winnerShare(5, 2));
        assertEquals(11, RegionalChallengeRules.winnerShare(5, 3));
        assertEquals(15, RegionalChallengeRules.winnerShare(5, 4));
        assertEquals(3, RegionalChallengeRules.winnerShare(2, 2));
        assertEquals(4, RegionalChallengeRules.winnerShare(2, 3));
        assertEquals(6, RegionalChallengeRules.winnerShare(2, 4));
    }

    @Test
    public void rankingUsesScoreThenDurationThenJoinOrder() {
        assertTrue(RegionalChallengeRules.compareResults(100, 50_000, 1, 90, 10_000, 0) < 0);
        assertTrue(RegionalChallengeRules.compareResults(100, 40_000, 1, 100, 50_000, 0) < 0);
        assertTrue(RegionalChallengeRules.compareResults(100, 40_000, 0, 100, 40_000, 1) < 0);
    }

    @Test
    public void challengeSessionExposesChallengeMetadata() {
        Question question = new Question("Pitanje", "A", "B", "C", "D", 1);
        SessionState session = new SessionState(
                "session-1",
                "challenge_run",
                "player",
                "ghost",
                1_000L,
                new GeneralKnowledgeGameState(Collections.singletonList(question)),
                "challenge-1",
                42L
        );

        JSONObject json = session.toJson();
        assertTrue(session.isChallengeRun());
        assertEquals("challenge-1", session.getChallengeId());
        assertEquals(42L, session.getChallengeContentSeed());
        assertEquals("challenge-1", json.getString("challengeId"));
        assertTrue(json.getBoolean("challengeRun"));
    }
}
