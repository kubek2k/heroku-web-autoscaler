package org.kubek2k.autoscaler.model;

public class TimeStats {
    public final Double avgServiceTime;

    public final Integer hitCount;

    public TimeStats(final Double avgServiceTime, final Integer hitCount) {
        this.avgServiceTime = avgServiceTime;
        this.hitCount = hitCount;
    }

    @Override
    public String toString() {
        return "TimeStats{" +
                "avgServiceTime=" + avgServiceTime +
                ", hitCount=" + hitCount +
                '}';
    }
}
