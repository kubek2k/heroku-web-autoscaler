package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.pure.redis.JedisUtil;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.kubek2k.autoscaler.model.StorageKeys;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsObserver extends ConfiguredCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsObserver.class);

    private final JedisUtil jedis;

    public StatsObserver(final JedisUtil jedis) {
        super("observe", "Observes stats and reacts");
        this.jedis = jedis;
    }

    @Override
    protected void run(final Bootstrap<StatsDrainConfiguration> bootstrap,
                       final Namespace namespace,
                       final StatsDrainConfiguration configuration) throws Exception {
        final String appName = "plan3-article-api";
        while (true) {
            final long lastObservation = Instant.now().getEpochSecond() - 10;
            try (Jedis jedis = this.jedis.nonTx()) {
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
                LOGGER.info("Last minute stats {}", lastMinuteStats);
            }
            Thread.sleep(10000);
        }
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
