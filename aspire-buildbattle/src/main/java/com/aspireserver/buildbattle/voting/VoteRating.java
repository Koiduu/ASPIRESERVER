package com.aspireserver.buildbattle.voting;

public enum VoteRating {
    SUPER_POOP(1),
    POOP(2),
    OKAY(3),
    GOOD(4),
    EPIC(5),
    LEGENDARY(6);

    private final int score;

    VoteRating(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    public static VoteRating fromScore(int score) {
        for (VoteRating rating : values()) {
            if (rating.score == score) return rating;
        }
        return OKAY;
    }
}
