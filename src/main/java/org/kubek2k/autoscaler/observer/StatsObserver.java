package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.ner.brute.model.RatioEntry;
import plan3.pure.redis.JedisUtil;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.model.StorageKeys;
import org.kubek2k.autoscaler.model.TimeStats;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.kubek2k.autoscaler.web.StatsDrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultiset;

public class StatsObserver extends EnvironmentCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsObserver.class);
    public static final int RATIO_CACHE_SIZE = 50;

    private final JedisUtil jedis;

    private final Deque<RatioEntry> ratioEntries = new LinkedList<>();

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
            final long lastObservation = Instant.now().getEpochSecond() - 10;
            final TimeStats latestStats = getTimeStats(appName, Instant.now().getEpochSecond() - 10);
            final List<TimeStats> lastMinuteStats = this.ratioEntries.stream()
                    .limit(6)
                    .map(RatioEntry::getTimeStats)
                    .collect(Collectors.toList());
            final int aggregatedHitCount = lastMinuteStats.stream()
                    .map(t -> t.hitCount)
                    .reduce((c1, c2) -> c1 + c2)
                    .get();
            final double aggregatedAvgServiceTime = lastMinuteStats.stream()
                    .map(t -> t.avgServiceTime * t.hitCount)
                    .reduce((t1, t2) -> t1 + t2)
                    .get() / aggregatedHitCount;

            final TimeStats aggregatedLastMinuteStats = new TimeStats(aggregatedAvgServiceTime, aggregatedHitCount);
            final int dynoCount = heroku.getNumberOfWebDynos(appName);
            final double ratio = countRatio(aggregatedLastMinuteStats, dynoCount, 60);
            LOGGER.info("Number of dynos of {}: {}. Ratio: {}. The new dyno count could be: {}", appName, dynoCount,
                    ratio, countNewDynoCount(latestStats, 400.0, ratio, 10));

            // new way
            this.ratioEntries.removeLast();
            this.ratioEntries.addFirst(new RatioEntry(lastObservation, countRatio(latestStats, dynoCount, 10),
                    latestStats));
            final double ratioMedian = countRatioMedian();
            LOGGER.info("Ratio median based on knowledge from the cache: {}. " +
                    "It would mean that new dyno count should be {}", ratioMedian,
                    countNewDynoCount(aggregatedLastMinuteStats, 400.0, ratioMedian, 60));

            Thread.sleep(10000);
        }
    }

    private TimeStats getTimeStats(final String appName, final long observation) {
        try (final Jedis jedis = this.jedis.nonTx()) {
            final Double serviceTime = getServiceTime(appName, jedis, observation);
            final Integer hitCount = getHitCount(appName, jedis, observation);
            return new TimeStats(serviceTime, hitCount);
        }
    }

    private void prefillRatioEntries(final String appName) {
        final long lastObservation = Instant.now().getEpochSecond() - 20;
        LOGGER.info("Prefilling cache");

        try(final Jedis jedis = this.jedis.nonTx()) {
            LongStream.iterate(lastObservation, i -> i - 10)
                    .limit(RATIO_CACHE_SIZE)
                    .mapToObj(observation -> {
                        final Double serviceTime = getServiceTime(appName, jedis, observation);
                        final Integer hitCount = getHitCount(appName, jedis, observation);
                        final Integer dynoCount = getDynoCount(appName, jedis, observation);
                        final TimeStats timeStats = new TimeStats(serviceTime, hitCount);
                        return new RatioEntry(observation, countRatio(timeStats, dynoCount, 10), timeStats);
                    })
                    .forEach(this.ratioEntries::add);
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

    public double countRatioMedian() {
        final TreeMultiset<RatioEntry> medianFinder = TreeMultiset.create(new Comparator<RatioEntry>() {
            @Override
            public int compare(final RatioEntry o1, final RatioEntry o2) {
                return o1.getRatio() - o2.getRatio() > 0 ? 1 : -1;
            }
        });
        medianFinder.addAll(this.ratioEntries);
        return Iterables.get(medianFinder, medianFinder.size() / 2).getRatio();
    }

    public double countRatio(final TimeStats periodStats, final int dynoCount, final long period) {
        if (periodStats.hitCount > 0) {
            return ((double) dynoCount) * periodStats.avgServiceTime * period / periodStats.hitCount;
        } else {
            return 0.0;
        }
    }

    public double countNewDynoCount(final TimeStats latestStats, final double desiredServiceTime, final double ratio, final long period) {
        return ((double) latestStats.hitCount * ratio) / desiredServiceTime / period;
    }

    private Optional<String> optGet(final Jedis jedis, final String key) {
        return Optional.ofNullable(jedis.get(key));
    }

}
