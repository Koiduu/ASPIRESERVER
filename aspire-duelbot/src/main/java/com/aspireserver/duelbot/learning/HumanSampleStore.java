package com.aspireserver.duelbot.learning;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * In-memory, bounded store of recorded human movement/knockback data with YAML persistence.
 *
 * <p>All mutation happens on the server main thread; {@link #snapshotForSave()} copies immutable
 * data so a save can be written off-thread without touching Bukkit APIs.</p>
 */
public final class HumanSampleStore {

    private final List<List<MovementSample>> movement = new ArrayList<>();
    private final List<KbTrace> kbTraces = new ArrayList<>();

    private int maxPerBucket = 3000;
    private int maxKbTraces = 800;

    public HumanSampleStore() {
        for (int i = 0; i < MovementSample.BUCKETS; i++) movement.add(new ArrayList<>());
    }

    public void configure(int maxPerBucket, int maxKbTraces) {
        this.maxPerBucket = Math.max(1, maxPerBucket);
        this.maxKbTraces = Math.max(1, maxKbTraces);
    }

    // ---- Recording (main thread) ----

    public synchronized void addMovement(int bucket, MovementSample sample) {
        if (bucket < 0 || bucket >= movement.size()) return;
        List<MovementSample> list = movement.get(bucket);
        if (list.size() >= maxPerBucket) {
            list.set(ThreadLocalRandom.current().nextInt(list.size()), sample); // reservoir-style overwrite
        } else {
            list.add(sample);
        }
    }

    public synchronized void addKbTrace(KbTrace trace) {
        if (trace == null || trace.length() == 0) return;
        if (kbTraces.size() >= maxKbTraces) {
            kbTraces.set(ThreadLocalRandom.current().nextInt(kbTraces.size()), trace);
        } else {
            kbTraces.add(trace);
        }
    }

    // ---- Sampling (main thread) ----

    public synchronized MovementSample sampleMovement(int bucket, Random rng) {
        if (bucket < 0 || bucket >= movement.size()) return null;
        List<MovementSample> list = movement.get(bucket);
        if (list.isEmpty()) return null;
        return list.get(rng.nextInt(list.size()));
    }

    /** Pick a KB recovery trace whose initial magnitudes best match the bot's own knockback. */
    public synchronized KbTrace sampleKbTrace(double hMag, double vMag, Random rng) {
        if (kbTraces.isEmpty()) return null;
        KbTrace best = null;
        double bestScore = Double.MAX_VALUE;
        int checked = 0;
        // Sample a random subset for a cheap nearest-context match, breaking ties randomly.
        int probes = Math.min(kbTraces.size(), 24);
        for (int i = 0; i < probes; i++) {
            KbTrace t = kbTraces.get(rng.nextInt(kbTraces.size()));
            double dh = t.hMag - hMag;
            double dv = t.vMag - vMag;
            double score = dh * dh + dv * dv + rng.nextDouble() * 0.01;
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
            checked++;
        }
        return best != null ? best : kbTraces.get(rng.nextInt(kbTraces.size()));
    }

    public synchronized int movementCount(int bucket) {
        return (bucket >= 0 && bucket < movement.size()) ? movement.get(bucket).size() : 0;
    }

    public synchronized int totalMovement() {
        int n = 0;
        for (List<MovementSample> l : movement) n += l.size();
        return n;
    }

    public synchronized int kbCount() {
        return kbTraces.size();
    }

    public synchronized void clear() {
        for (List<MovementSample> l : movement) l.clear();
        kbTraces.clear();
    }

    // ---- Persistence ----

    /** Immutable snapshot for off-thread saving. */
    public synchronized Snapshot snapshotForSave() {
        List<List<MovementSample>> mv = new ArrayList<>();
        for (List<MovementSample> l : movement) mv.add(new ArrayList<>(l));
        return new Snapshot(mv, new ArrayList<>(kbTraces));
    }

    public static void save(Snapshot snap, File file, Logger log) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int b = 0; b < snap.movement.size(); b++) {
            List<String> encoded = new ArrayList<>();
            for (MovementSample s : snap.movement.get(b)) {
                encoded.add(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%d,%d",
                        s.forward(), s.strafe(), s.jump() ? 1 : 0, s.sneak() ? 1 : 0));
            }
            yaml.set("movement." + MovementSample.bucketName(b), encoded);
        }
        List<String> kb = new ArrayList<>();
        for (KbTrace t : snap.kbTraces) kb.add(t.encode());
        yaml.set("kb-traces", kb);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Failed to save human samples: " + e.getMessage());
        }
    }

    public synchronized void load(File file, Logger log) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int b = 0; b < movement.size(); b++) {
            movement.get(b).clear();
            List<String> encoded = yaml.getStringList("movement." + MovementSample.bucketName(b));
            for (String s : encoded) {
                String[] c = s.split(",");
                if (c.length < 4) continue;
                movement.get(b).add(new MovementSample(
                        parse(c[0]), parse(c[1]), c[2].trim().equals("1"), c[3].trim().equals("1")));
            }
        }
        kbTraces.clear();
        for (String s : yaml.getStringList("kb-traces")) {
            KbTrace t = KbTrace.decode(s);
            if (t != null) kbTraces.add(t);
        }
        log.info("Loaded human samples: " + totalMovement() + " movement, " + kbTraces.size() + " kb traces.");
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Immutable copy used for off-thread persistence. */
    public static final class Snapshot {
        final List<List<MovementSample>> movement;
        final List<KbTrace> kbTraces;

        Snapshot(List<List<MovementSample>> movement, List<KbTrace> kbTraces) {
            this.movement = movement;
            this.kbTraces = kbTraces;
        }
    }
}
