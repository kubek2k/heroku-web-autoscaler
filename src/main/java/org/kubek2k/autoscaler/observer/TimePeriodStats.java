package org.kubek2k.autoscaler.observer;

import java.util.Optional;

public class TimePeriodStats {
    private final long periodStartTimestamp;

    private final long periodLength;

    private final long avgDynoCount;

    private final double avgServiceTime;

    public final int hitCount;
    private Double hitRate;

    public TimePeriodStats(final long periodStartTimestamp,
                           final long periodLength,
                           final long avgDynoCount,
                           final double avgServiceTime,
                           final int hitCount) {
        this.periodStartTimestamp = periodStartTimestamp;
        this.avgDynoCount = avgDynoCount;
        this.periodLength = periodLength;
        this.avgServiceTime = avgServiceTime;
        this.hitCount = hitCount;
    }

    public Optional<Double> getRatio() {
        if(this.hitCount > 0) {
            return Optional.of(((double) this.avgDynoCount) * this.avgServiceTime * this.periodLength / this.hitCount);
        }
        else {
            return Optional.empty();
        }
    }

    public TimePeriodStats aggregate(final TimePeriodStats other) {
        final long newPeriodLength = this.periodLength + other.periodLength;
        return new TimePeriodStats(Math.min(this.periodStartTimestamp, other.periodStartTimestamp),
                newPeriodLength,
                (this.avgDynoCount * this.periodLength + other.avgDynoCount * other.periodLength) / newPeriodLength,
                (this.avgServiceTime * this.periodLength + other.avgServiceTime * other.periodLength) / newPeriodLength,
                this.hitCount + other.hitCount);
    }

    public Double getHitRate() {
        return (double) this.periodLength / this.hitCount;
    }

    @Override
    public String toString() {
        return "TimePeriodStats{" +
                "periodStartTimestamp=" + periodStartTimestamp +
                ", periodLength=" + periodLength +
                ", avgDynoCount=" + avgDynoCount +
                ", avgServiceTime=" + avgServiceTime +
                ", hitCount=" + hitCount +
                ", hitRate=" + hitRate +
                '}';
    }
}
