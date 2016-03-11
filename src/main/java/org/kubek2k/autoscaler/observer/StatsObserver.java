package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.ner.brute.model.RatioEntry;
import plan3.pure.redis.JedisUtil;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.Optional;
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
        prefillRatioEntries(appName);
        while(true) {
            final long lastObservation = Instant.now().getEpochSecond() - Granularity.GRANULARITY;
            final TimeStats mostRecentStats = getTimeStats(appName, Instant.now().getEpochSecond() - Granularity.GRANULARITY);
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

    private TimeStats getTimeStats(final String appName, final long pointInTime) {
        try (final Jedis jedis = this.jedis.nonTx()) {
            final Double serviceTime = getServiceTime(appName, jedis, pointInTime);
            final Integer hitCount = getHitCount(appName, jedis, pointInTime);
            return new TimeStats(serviceTime, hitCount);
        }
    }

    private void prefillRatioEntries(final String appName) {
        final long lastObservation = Instant.now().getEpochSecond() - 2*Granularity.GRANULARITY;
        LOGGER.info("Prefilling cache");
        try(final Jedis jedis = this.jedis.nonTx()) {
            LongStream.iterate(lastObservation, i -> i - Granularity.GRANULARITY)
                    .limit(RATIO_CACHE_SIZE)
                    .mapToObj(observation -> {
                        final Integer dynoCount = getDynoCount(appName, jedis, observation);
                        final TimeStats timeStats = getTimeStats(appName, observation);
                        return new RatioEntry(observation, dynoCount, timeStats, Granularity.GRANULARITY);
                    })
                    .forEach(this.ratioEntriesCache::add);
        }
        LOGGER.info("Prefilling done");
    }

    private Integer getDynoCount(final String appName, final Jedis jedis, final long observation) {
        return optGet(jedis, StorageKeys.numberOfDynosId(appName, observation))
                .map(Integer::parseInt)
                .orElse(8); // TODO this should be value_minimal
    }

    private Integer getHitCount(final String appName, final Jedis jedis, final long observation) {
        return optGet(jedis, StorageKeys.counterId(appName, observation))
                .map(Integer::parseInt)
                .orElse(0);
    }

    private Double getServiceTime(final String appName, final Jedis jedis, final long observation) {
        return optGet(jedis, StorageKeys.avgServiceTimeId(appName, observation))
                                    .map(Double::parseDouble)
                                    .orElse(0.0d);
    }

    public double countNewDynoCount(final TimeStats latestStats, final double desiredServiceTime, final double ratio, final long period) {
        return ((double) latestStats.hitCount * ratio) / desiredServiceTime / period;
    }

    private Optional<String> optGet(final Jedis jedis, final String key) {
        return Optional.ofNullable(jedis.get(key));
    }

}
