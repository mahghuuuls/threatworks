package com.mahghuuuls.threatworks.server;

/** Counters for one bounded window of world tick work, excluding commands and hit processing. */
final class WorkSample {
    private final int duration;
    private int ticks;
    private int firstTick;
    private int lastTick;
    private int minActive = Integer.MAX_VALUE;
    private int maxActive;
    private int visited;
    private int selections;
    private int observations;
    private int paths;
    private int maxSelectionsPerTick;
    private int maxPathsPerTick;
    private int previousSelections;
    private int previousPaths;
    private int supplements;
    private int lastSupplementTick;
    private int supplementsThisTick;
    private int maxSupplementsPerTick;

    WorkSample(int duration) {
        if (duration < 1 || duration > 1000) {
            throw new IllegalArgumentException("Sample duration must be between 1 and 1000 ticks");
        }
        this.duration = duration;
    }

    void visit() {
        visited++;
    }

    void selection() {
        selections++;
    }

    void observation() {
        observations++;
    }

    void pathQuery() {
        paths++;
    }

    void supplement(int tick) {
        if (tick != lastSupplementTick) {
            lastSupplementTick = tick;
            supplementsThisTick = 0;
        }
        supplements++;
        maxSupplementsPerTick = Math.max(maxSupplementsPerTick, ++supplementsThisTick);
    }

    void endTick(int tick, int active) {
        if (ticks++ == 0) {
            firstTick = tick;
        }
        lastTick = tick;
        minActive = Math.min(minActive, active);
        maxActive = Math.max(maxActive, active);
        maxSelectionsPerTick = Math.max(maxSelectionsPerTick, selections - previousSelections);
        maxPathsPerTick = Math.max(maxPathsPerTick, paths - previousPaths);
        previousSelections = selections;
        previousPaths = paths;
    }

    boolean complete() {
        return ticks == duration;
    }

    String report() {
        return "work firstTick=" + firstTick + " lastTick=" + lastTick + " ticks=" + ticks
                + " activeMin=" + minActive + " activeMax=" + maxActive + " visited=" + visited
                + " selections=" + selections + " observations=" + observations + " paths=" + paths
                + " peakSelections=" + maxSelectionsPerTick + " peakPaths=" + maxPathsPerTick
                + " supplements=" + supplements + " peakSupplements=" + maxSupplementsPerTick
                + " cadence=" + com.mahghuuuls.threatworks.threat.Encounter.SELECTION_INTERVAL;
    }
}
