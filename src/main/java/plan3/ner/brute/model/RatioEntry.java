package plan3.ner.brute.model;

import org.kubek2k.autoscaler.model.TimeStats;

public class RatioEntry {

    private final long epochTimestamp;

    private final double ratio;

    private final TimeStats timeStats;

    public RatioEntry(final long epochTimestamp, final double ratio, final TimeStats timeStats) {
        this.epochTimestamp = epochTimestamp;
        this.ratio = ratio;
        this.timeStats = timeStats;
    }

    public long getEpochTimestamp() {
        return this.epochTimestamp;
    }

    public double getRatio() {
        return this.ratio;
    }

    public TimeStats getTimeStats() {
        return timeStats;
    }
}

