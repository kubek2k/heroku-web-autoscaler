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
            try(Jedis jedis = this.jedis.nonTx()) {
                final List<TimeStats> lastMinuteStats = LongStream.iterate(lastObservation, i -> i - 10)
                        .limit(6)
                        .mapToObj(observation -> {
                            final Double serviceTime = optGet(jedis, StorageKeys.avgServiceTimeId(appName, observation))
                                    .map(Double::parseDouble)
                                    .orElse(0.0d);
                            final Integer hitCount = optGet(jedis, StorageKeys.counterId(appName, observation))
                                    .map(Integer::parseInt)
                                    .orElse(0);
                            return new TimeStats(serviceTime, hitCount);
                        })
                        .collect(Collectors.toList());
                final TimeStats latest = lastMinuteStats.get(0);
                final int aggregatedHitCount = lastMinuteStats.stream()
                        .map(t -> t.hitCount)
                        .reduce((c1, c2) -> c1 + c2)
                        .get();
                final double aggregatedAvgServiceTime = lastMinuteStats.stream()
                        .map(t -> t.avgServiceTime * t.hitCount)
                        .reduce((t1, t2) -> t1 + t2)
                        .get() / aggregatedHitCount;

                final TimeStats aggregatedLastMinuteStats = new TimeStats(aggregatedAvgServiceTime, aggregatedHitCount);
                LOGGER.info("Last minute stats {}. Aggregated {}", lastMinuteStats, aggregatedLastMinuteStats);
                final int dynoCount = heroku.getNumberOfWebDynos(appName);
                final double ratio = countRatio(aggregatedLastMinuteStats, dynoCount);
                LOGGER.info("Number of dynos of {}: {}. Ratio: {}. The new dyno count could be: {}", appName, dynoCount,
                        ratio, countNewDynoCount(latest, 400.0, ratio));

                // new way
                this.ratioEntries.removeLast();
                this.ratioEntries.addFirst(new RatioEntry(lastObservation, countRatio(latest, dynoCount)));
                final double ratioMedian = countRatioMedian();
                LOGGER.info("Ratio median based on knowledge from the cache: {}. It would mean that new dyno count should be {}",
                        ratioMedian, countNewDynoCount(latest, 400.0, ratioMedian));

            }
            Thread.sleep(10000);
        }
    }

    private void prefillRatioEntries(final String appName) {
        final long lastObservation = Instant.now().getEpochSecond() - 20;
        LOGGER.info("Prefilling cache");

        try(Jedis jedis = this.jedis.nonTx()) {
            LongStream.iterate(lastObservation, i -> i - 10)
                    .limit(RATIO_CACHE_SIZE)
                    .mapToObj(observation -> {
                        final Double serviceTime = optGet(jedis, StorageKeys.avgServiceTimeId(appName, observation))
                                .map(Double::parseDouble)
                                .orElse(0.0d);
                        final Integer hitCount = optGet(jedis, StorageKeys.counterId(appName, observation))
                                .map(Integer::parseInt)
                                .orElse(0);
                        final Integer dynoCount = optGet(jedis, StorageKeys.numberOfDynosId(appName, observation))
                                .map(Integer::parseInt)
                                .orElse(8); // TODO this should be value_minimal
                        return new RatioEntry(observation, countRatio(new TimeStats(serviceTime, hitCount), dynoCount));
                    })
                    .forEach(this.ratioEntries::add);
        }

        LOGGER.info("Prefilling done");
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

    public double countRatio(final TimeStats lastMinuteStats, final int dynoCount) {
        return ((double) dynoCount) * lastMinuteStats.avgServiceTime / lastMinuteStats.hitCount;
    }

    public double countNewDynoCount(final TimeStats latestStats, final double desiredServiceTime, final double ratio) {
        return ((double) latestStats.hitCount * ratio) / desiredServiceTime;
    }

    private Optional<String> optGet(final Jedis jedis, final String key) {
        return Optional.ofNullable(jedis.get(key));
    }

    public static class TimeStats {
        private final Double avgServiceTime;

        private final Integer hitCount;

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
}
