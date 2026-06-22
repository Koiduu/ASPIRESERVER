package com.aspireserver.buildbattle.voting;

public enum VoteRating {
    F(1),
    D(2),
    E(3),
    C(4),
    B(5),
    A(6),
    S(7);

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
        return C;
    }
}
