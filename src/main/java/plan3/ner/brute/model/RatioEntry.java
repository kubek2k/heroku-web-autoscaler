package plan3.ner.brute.model;

import org.kubek2k.autoscaler.model.TimeStats;

public class RatioEntry {

    private final long epochTimestamp;

    private final int dynoCount;

    private final TimeStats timeStats;

    public final Double avgServiceTime;

    public final Integer hitCount;

    private final long period;

    public RatioEntry(final long epochTimestamp,
                      final int dynoCount,
                      final TimeStats timeStats,
                      final long period) {
        this.epochTimestamp = epochTimestamp;
        this.dynoCount = dynoCount;
        this.timeStats = timeStats;
        this.avgServiceTime = timeStats.avgServiceTime;
        this.hitCount = timeStats.hitCount;
        this.period = period;
    }

    public long getEpochTimestamp() {
        return this.epochTimestamp;
    }

    public double getRatio() {
        if (this.hitCount > 0) {
            return ((double) this.dynoCount) * this.avgServiceTime * this.period / this.hitCount;
        } else {
            return 0.0;
        }
    }

    public TimeStats getTimeStats() {
        return this.timeStats;
    }

    @Override
    public String toString() {
        return "RatioEntry{" +
                "epochTimestamp=" + epochTimestamp +
                ", dynoCount=" + dynoCount +
                ", timeStats=" + timeStats +
                ", avgServiceTime=" + avgServiceTime +
                ", hitCount=" + hitCount +
                ", period=" + period +
                '}';
    }
}

