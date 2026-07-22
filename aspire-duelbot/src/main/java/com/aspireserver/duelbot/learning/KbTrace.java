package com.aspireserver.duelbot.learning;

/**
 * A recorded post-knockback recovery trajectory from a real player: the per-tick
 * velocity for the ticks following a hit, stored in a frame aligned to the
 * knockback direction so it can be re-projected onto a bot's own knockback.
 *
 * <p>Each row of {@link #vel} is {@code [along, up, side]}:
 * <ul>
 *   <li>{@code along} — velocity along the horizontal knockback direction (away from attacker)</li>
 *   <li>{@code up} — raw vertical velocity</li>
 *   <li>{@code side} — velocity perpendicular to the knockback direction (recovery strafe)</li>
 * </ul>
 * {@code hMag}/{@code vMag} are the initial knockback magnitudes (the context this trace belongs to).</p>
 */
public final class KbTrace {

    public final double hMag;
    public final double vMag;
    public final double[][] vel;

    public KbTrace(double hMag, double vMag, double[][] vel) {
        this.hMag = hMag;
        this.vMag = vMag;
        this.vel = vel;
    }

    public int length() {
        return vel.length;
    }

    /** Serialise to a flat string: "hMag;vMag;along,up,side|along,up,side|...". */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(fmt(hMag)).append(';').append(fmt(vMag)).append(';');
        for (int i = 0; i < vel.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(fmt(vel[i][0])).append(',').append(fmt(vel[i][1])).append(',').append(fmt(vel[i][2]));
        }
        return sb.toString();
    }

    public static KbTrace decode(String s) {
        String[] head = s.split(";", 3);
        if (head.length < 3) return null;
        double h = parse(head[0]);
        double v = parse(head[1]);
        String[] rows = head[2].split("\\|");
        double[][] vel = new double[rows.length][3];
        for (int i = 0; i < rows.length; i++) {
            String[] c = rows[i].split(",");
            if (c.length < 3) return null;
            vel[i][0] = parse(c[0]);
            vel[i][1] = parse(c[1]);
            vel[i][2] = parse(c[2]);
        }
        return new KbTrace(h, v, vel);
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.4f", d);
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
