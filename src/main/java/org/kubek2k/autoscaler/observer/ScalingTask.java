package org.kubek2k.autoscaler.observer;

import java.time.Instant;
import java.util.Optional;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.librato.PoorMansLibrato;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ScalingTask implements Runnable {
    private static final int LOOKBACK_WINDOW_SIZE = 60;
    private final Logger logger;
    private final String appName;
    private final Heroku heroku;
    private final PoorMansLibrato.MeasureReporter ratioMedianReporter;
    private final PoorMansLibrato.MeasureReporter inferredDynoCountReporter;
    private final PoorMansLibrato.MeasureReporter scaledDynoCount;
    private final PoorMansLibrato.MeasureReporter hitRateReporter;
    private final Double targetAverageServiceTime;
    private final TimePeriodStatsCache timePeriodStatsCache;
    private final ScalingDecision scalingDecision = new ScalingDecision();

    public ScalingTask(final String appName,
                       final Heroku heroku,
                       final Double targetAverageServiceTime,
                       final PoorMansLibrato librato,
                       final TimePeriodStatsCache timePeriodStatsCache) {
        this.appName = appName;
        this.heroku = heroku;
        this.targetAverageServiceTime = targetAverageServiceTime;
        this.timePeriodStatsCache = timePeriodStatsCache;
        this.inferredDynoCountReporter = librato.sampleReporter("inferred-dyno-count", "dynos", Optional.of(appName));
        this.ratioMedianReporter = librato.sampleReporter("ratio-median", "", Optional.of(appName));
        this.scaledDynoCount = librato.sampleReporter("scaled-dyno-count", "dynos", Optional.of(appName));
        this.hitRateReporter = librato.sampleReporter("hit-rate", "", Optional.of(appName));
        this.logger = LoggerFactory.getLogger(ScalingTask.class.getCanonicalName() + "-" + appName);
    }

    @Override
    public void run() {
        try {
            final long lastObservation = Instant.now().getEpochSecond() - Granularity.GRANULARITY;
            final TimePeriodStats mostRecentStats = this.timePeriodStatsCache.getTimePeriodStats(this.appName,
                    lastObservation,
                    this.heroku);
            this.logger.info("Most recent ratio is {} for time stats {}",
                    mostRecentStats.getRatio(),
                    mostRecentStats);
            final TimePeriodStats aggregatedLastMinuteStats = this.timePeriodStatsCache.aggregateBack(
                    LOOKBACK_WINDOW_SIZE);
            this.timePeriodStatsCache.addStats(mostRecentStats);
            final Optional<Double> ratioMedian = this.timePeriodStatsCache.countRatioMedian();
            ratioMedian.ifPresent(ratio -> {
                final Double inferredDynoCount = countNewDynoCount(aggregatedLastMinuteStats,
                        this.targetAverageServiceTime,
                        ratio);
                this.logger.info("Ratio median based on knowledge from the cache: {}. " +
                                "It would mean that new dyno count for last minute stats {} should be {}",
                        this.ratioMedianReporter.report(ratio),
                        aggregatedLastMinuteStats,
                        this.inferredDynoCountReporter.report(inferredDynoCount));
                this.hitRateReporter.report(mostRecentStats.getHitRate());
                final int newDynoCount = (int) Math.ceil(inferredDynoCount);
                if(this.scalingDecision.shouldIScale(this.appName,
                        mostRecentStats.getAvgDynoCount(),
                        newDynoCount)) {
                    this.scaledDynoCount.report(newDynoCount);
                    this.heroku.scale(this.appName, newDynoCount);
                }
                else {
                    this.scaledDynoCount.report(mostRecentStats.getAvgDynoCount());
                }
            });
        }
        catch(final Exception e) {
            this.logger.warn("Decision making for " + this.appName + " failed ", e);
        }
    }

    private Double countNewDynoCount(final TimePeriodStats latestStats,
                                    final double desiredServiceTime,
                                    final double ratio) {
        return (latestStats.getHitRate() * ratio) / desiredServiceTime;
    }
}
