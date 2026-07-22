package com.aspireserver.bedwars.game;

public enum GameState {
    WAITING,      // Lobby, waiting for players
    COUNTDOWN,    // Enough players, counting down to start
    RUNNING,      // Match in progress
    SUDDEN_DEATH, // Late-game dragons
    ENDING        // Match over, showing winner
}
