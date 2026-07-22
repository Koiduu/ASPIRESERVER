package com.aspireserver.duelbot.learning;

/**
 * One tick of a real player's in-combat micro-movement, expressed relative to the
 * direction to their opponent so it can be replayed by a bot facing any way.
 *
 * <p>{@code forward} is the movement component toward the opponent (-1 back .. +1 in),
 * {@code strafe} is the perpendicular (circling) component (-1 left .. +1 right),
 * both normalised to walking speed. {@code jump}/{@code sneak} capture input state.</p>
 */
public record MovementSample(double forward, double strafe, boolean jump, boolean sneak) {

    public static final int BUCKET_CLOSE = 0;
    public static final int BUCKET_MID = 1;
    public static final int BUCKET_FAR = 2;
    public static final int BUCKETS = 3;

    /** Distance-to-opponent bucket used to key movement samples. */
    public static int bucketOf(double distance) {
        if (distance < 2.2) return BUCKET_CLOSE;
        if (distance < 3.8) return BUCKET_MID;
        return BUCKET_FAR;
    }

    public static String bucketName(int bucket) {
        return switch (bucket) {
            case BUCKET_CLOSE -> "CLOSE";
            case BUCKET_MID -> "MID";
            default -> "FAR";
        };
    }
}
