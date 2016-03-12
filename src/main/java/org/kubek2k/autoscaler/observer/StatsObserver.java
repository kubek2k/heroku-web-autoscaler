package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.pure.redis.JedisUtil;
import plan3.pure.redis.Tx;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.librato.PoorMansLibrato;
import org.kubek2k.autoscaler.model.StorageKeys;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.kubek2k.autoscaler.web.StatsDrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsObserver extends EnvironmentCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsObserver.class);
    private static final int RATIO_CACHE_SIZE = 50;
    private static final int LOOKBACK_WINDOW_SIZE = 60;

    private final TimePeriodStatsCache timePeriodStatsCache = new TimePeriodStatsCache();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private final PoorMansLibrato librato = new PoorMansLibrato("heroku.web.autoscaler");
    private final JedisUtil jedis;

    public StatsObserver(final StatsDrainService service, final JedisUtil jedis) {
        super(service, "observe", "Observes stats and reacts");
        this.jedis = jedis;
    }

    @Override
    protected void run(final Environment environment,
                       final Namespace namespace,
                       final StatsDrainConfiguration configuration) throws Exception {
        final String appName = "plan3-article-api";
        final Heroku heroku = configuration.heroku(environment);
        final PoorMansLibrato.MeasureReporter inferredDynoCountReporter = this.librato.sampleReporter(
                "inferred-dyno-count",
                "dynos",
                Optional.of(appName));
        final PoorMansLibrato.MeasureReporter ratioMedianReporter = this.librato.sampleReporter("ratio-median",
                "",
                Optional.of(appName));
        prefillRatioEntries(appName, heroku.getNumberOfWebDynos(appName));
        this.executorService.scheduleAtFixedRate(() -> {
            try {
                final long lastObservation = Instant.now().getEpochSecond() - Granularity.GRANULARITY;
                final TimePeriodStats mostRecentStats = getTimePeriodStats(appName, lastObservation, heroku);
                final TimePeriodStats aggregatedLastMinuteStats = StatsObserver.this.timePeriodStatsCache.aggregateBack(
                        LOOKBACK_WINDOW_SIZE);
                StatsObserver.this.timePeriodStatsCache.addNewRatioEntry(mostRecentStats);
                final double ratioMedian = StatsObserver.this.timePeriodStatsCache.countRatioMedian();
                final double inferredDynoCount = countNewDynoCount(aggregatedLastMinuteStats,
                        400.0,
                        ratioMedian);
                LOGGER.info(
                        "Ratio median based on knowledge from the cache: {}. It would mean that new dyno count should be {}",
                        ratioMedianReporter.report(ratioMedian),
                        inferredDynoCountReporter.report(inferredDynoCount));
            }
            catch(final Exception e) {
                LOGGER.warn("Decision making for " + appName + " failed ", e);
            }
        }, 0, Granularity.GRANULARITY, TimeUnit.SECONDS).get();
        this.executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS);
    }

    private TimePeriodStats getTimePeriodStats(final String appName,
                                               final long pointInTime,
                                               final Heroku heroku) throws ExecutionException {
        final Object[] responses;
        try(final Tx tx = this.jedis.tx()) {
            responses = getTimeStatsResponses(appName, pointInTime, tx.redis());
        }
        final Double avgServiceTime = extractAvgServiceTime(responses);
        final Integer hitCount = extractHitCount(responses);
        return new TimePeriodStats(pointInTime,
                Granularity.GRANULARITY,
                heroku.getNumberOfWebDynos(appName),
                avgServiceTime,
                hitCount);
    }

    private Object[] getTimeStatsResponses(final String appName, final long pointInTime, final Transaction tx) {
        final Response<String> avgServiceTime = tx.get(StorageKeys.avgServiceTimeId(appName, pointInTime));
        final Response<String> hitCount = tx.get(StorageKeys.counterId(appName, pointInTime));
        return new Object[]{pointInTime, avgServiceTime, hitCount};
    }

    private void prefillRatioEntries(final String appName, final int initialDynoCount) {
        final long lastObservation = Instant.now().getEpochSecond() - 2 * Granularity.GRANULARITY;
        LOGGER.info("Prefilling cache");
        getTimeStatsInOneShot(appName, lastObservation)
                .stream()
                .map(pair -> {
                    final Long epochTimestamp = (Long) pair[0];
                    final Double avgServiceTime = extractAvgServiceTime(pair);
                    final Integer hitCount = extractHitCount(pair);
                    return new TimePeriodStats(epochTimestamp,
                            Granularity.GRANULARITY,
                            initialDynoCount,
                            avgServiceTime,
                            hitCount);
                })
                .forEach(this.timePeriodStatsCache::add);
        LOGGER.info("Prefilling done {}", this.timePeriodStatsCache);
    }

    private Integer extractHitCount(final Object[] responseArr) {
        return Optional.ofNullable(((Response<String>) responseArr[2]).get())
                .map(Integer::parseInt)
                .orElse(0);
    }

    private Double extractAvgServiceTime(final Object[] responseArr) {
        return Optional.ofNullable(((Response<String>) responseArr[1]).get())
                .map(Double::parseDouble)
                .orElse(0.0);
    }

    private List<Object[]> getTimeStatsInOneShot(final String appName, final long lastObservation) {
        try(final Tx tx = this.jedis.tx()) {
            return LongStream.iterate(lastObservation,
                    i -> i - Granularity.GRANULARITY)
                    .limit(RATIO_CACHE_SIZE)
                    .mapToObj(observation -> getTimeStatsResponses(appName, observation, tx.redis()))
                    .collect(Collectors.toList());
        }
    }

    public double countNewDynoCount(final TimePeriodStats latestStats,
                                    final double desiredServiceTime,
                                    final double ratio) {
        return (latestStats.getHitRate() * ratio) / desiredServiceTime;
    }
}
