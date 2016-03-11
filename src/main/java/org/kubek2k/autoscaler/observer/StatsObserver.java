package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.ner.brute.model.RatioEntry;
import plan3.pure.redis.JedisUtil;
import plan3.pure.redis.Tx;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.model.StorageKeys;
import org.kubek2k.autoscaler.model.TimeStats;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.kubek2k.autoscaler.web.StatsDrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsObserver extends EnvironmentCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsObserver.class);
    private static final int RATIO_CACHE_SIZE = 50;
    private static final int LOOKBACK_WINDOW_SIZE = 60;

    private final RatioEntriesCache ratioEntriesCache = new RatioEntriesCache();
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
        prefillRatioEntries(appName, heroku.getNumberOfWebDynos(appName));
        while(true) {
            final long lastObservation = Instant.now().getEpochSecond() - Granularity.GRANULARITY;
            final TimeStats mostRecentStats = getTimeStats2(appName,
                    Instant.now().getEpochSecond() - Granularity.GRANULARITY);
            final TimeStats aggregatedLastMinuteStats = this.ratioEntriesCache.aggregateBack(LOOKBACK_WINDOW_SIZE);
            final int currentDynoCount = heroku.getNumberOfWebDynos(appName);
            this.ratioEntriesCache.addNewRatioEntry(lastObservation, mostRecentStats, currentDynoCount);
            final double ratioMedian = this.ratioEntriesCache.countRatioMedian();
            LOGGER.info("Ratio median based on knowledge from the cache: {}. " +
                            "It would mean that new dyno count should be {}", ratioMedian,
                    countNewDynoCount(aggregatedLastMinuteStats, 400.0, ratioMedian, LOOKBACK_WINDOW_SIZE));

            Thread.sleep(10000);
        }
    }

    private TimeStats getTimeStats2(final String appName, final long pointInTime) {
        final Object [] responses;
        try (final Tx tx = this.jedis.tx()) {
            responses = getTimeStatsResponses(appName, pointInTime, tx.redis());
        }
        return new TimeStats(extractAvgServiceTime(responses), extractHitCount(responses));
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
                    return new RatioEntry(epochTimestamp,
                            initialDynoCount,
                            new TimeStats(avgServiceTime, hitCount),
                            10);
                })
                .forEach(this.ratioEntriesCache::add);
        LOGGER.info("Prefilling done {}", this.ratioEntriesCache);
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

    public double countNewDynoCount(final TimeStats latestStats,
                                    final double desiredServiceTime,
                                    final double ratio,
                                    final long period) {
        return ((double) latestStats.hitCount * ratio) / desiredServiceTime / period;
    }
}
