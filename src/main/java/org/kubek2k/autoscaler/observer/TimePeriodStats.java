package org.kubek2k.autoscaler.observer;

public class TimePeriodStats {
    private final long periodStartTimestamp;

    private final long periodLength;

    private final long avgDynoCount;

    private final double avgServiceTime;

    public final int hitCount;

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

    public double getRatio() {
        if (this.hitCount > 0) {
            return ((double) this.avgDynoCount) * this.avgServiceTime * this.periodLength / this.hitCount;
        } else {
            return 0.0;
        }
    }

    public TimePeriodStats aggregate(final TimePeriodStats other) {
        final long newPeriodLength = this.periodLength + other.periodLength;
        return new TimePeriodStats(Math.min(this.periodStartTimestamp, other.periodStartTimestamp),
                newPeriodLength,
                (this.avgDynoCount*this.periodLength + other.avgDynoCount*other.periodLength) / newPeriodLength,
                (this.avgServiceTime*this.periodLength + other.avgServiceTime*other.periodLength) / newPeriodLength,
                this.hitCount + other.hitCount);
    }
}
