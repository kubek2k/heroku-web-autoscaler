package org.kubek2k.autoscaler.observer;

import java.util.Optional;

public class TimePeriodStats {
    private final long periodStartTimestamp;

    private final long periodLength;

    private final int avgDynoCount;

    private final double avgServiceTime;

    public final int hitCount;

    public TimePeriodStats(final long periodStartTimestamp,
                           final long periodLength,
                           final int avgDynoCount,
                           final double avgServiceTime,
                           final int hitCount) {
        this.periodStartTimestamp = periodStartTimestamp;
        this.avgDynoCount = avgDynoCount;
        this.periodLength = periodLength;
        this.avgServiceTime = avgServiceTime;
        this.hitCount = hitCount;
    }

    public Optional<Double> getRatio() {
        if(this.getHitRate() > 0.0) {
            final Double loadPerHit = this.avgServiceTime / this.getHitRate();
            final long uniformlyDistributedHits = (this.hitCount / this.avgDynoCount) * this.avgDynoCount;
            final long nonUniformlyDistributedHits = this.hitCount % this.avgDynoCount;

            return Optional.of((uniformlyDistributedHits + nonUniformlyDistributedHits) / this.hitCount * loadPerHit);
        }
        else {
            return Optional.empty();
        }
    }

    public TimePeriodStats aggregate(final TimePeriodStats other) {
        final long newPeriodLength = this.periodLength + other.periodLength;
        return new TimePeriodStats(Math.min(this.periodStartTimestamp, other.periodStartTimestamp),
                newPeriodLength,
                (int)((this.avgDynoCount * this.periodLength + other.avgDynoCount * other.periodLength) / newPeriodLength),
                (this.avgServiceTime * this.periodLength + other.avgServiceTime * other.periodLength) / newPeriodLength,
                this.hitCount + other.hitCount);
    }

    public Double getHitRate() {
        return (double) this.hitCount / this.periodLength;
    }

    public int getAvgDynoCount() {
        return this.avgDynoCount;
    }

    @Override
    public String toString() {
        return "TimePeriodStats{" +
                "periodStartTimestamp=" + periodStartTimestamp +
                ", periodLength=" + periodLength +
                ", avgDynoCount=" + avgDynoCount +
                ", avgServiceTime=" + avgServiceTime +
                ", hitCount=" + hitCount +
                ", hitRate=" + this.getHitRate() +
                ", ratio=" + this.getRatio() +
                '}';
    }
}
