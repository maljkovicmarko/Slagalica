package com.example.slagalica.Model;

public class GameSession {

    private String player1UID;
    private String player2UID;
    private String type; // "ranked" or "friendly"
    private String status; // "active", "finished", "abandoned"
    private String currentGame;
    private String currentTurnUID;
    private int player1TotalScore;
    private int player2TotalScore;
    private String winnerUID;

    public GameSession() {
    }

    public GameSession(String player1UID, String player2UID, String type) {
        this.player1UID = player1UID;
        this.player2UID = player2UID;
        this.type = type;
        this.status = "active";
        this.currentGame = "slagalica";
        this.currentTurnUID = player1UID;
        this.player1TotalScore = 0;
        this.player2TotalScore = 0;
        this.winnerUID = null;
    }

    public String getPlayer1UID() { return player1UID; }
    public void setPlayer1UID(String player1UID) { this.player1UID = player1UID; }

    public String getPlayer2UID() { return player2UID; }
    public void setPlayer2UID(String player2UID) { this.player2UID = player2UID; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentGame() { return currentGame; }
    public void setCurrentGame(String currentGame) { this.currentGame = currentGame; }

    public String getCurrentTurnUID() { return currentTurnUID; }
    public void setCurrentTurnUID(String currentTurnUID) { this.currentTurnUID = currentTurnUID; }

    public int getPlayer1TotalScore() { return player1TotalScore; }
    public void setPlayer1TotalScore(int player1TotalScore) { this.player1TotalScore = player1TotalScore; }

    public int getPlayer2TotalScore() { return player2TotalScore; }
    public void setPlayer2TotalScore(int player2TotalScore) { this.player2TotalScore = player2TotalScore; }

    public String getWinnerUID() { return winnerUID; }
    public void setWinnerUID(String winnerUID) { this.winnerUID = winnerUID; }
}