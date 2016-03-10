package org.kubek2k.autoscaler.statsconsumer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.msg.queue.QueueConsumer;
import plan3.pure.redis.JedisUtil;
import plan3.restin.jackson.JsonUtil;
import redis.clients.jedis.Jedis;

import java.util.Optional;

import org.kubek2k.autoscaler.model.RouterEntries;
import org.kubek2k.autoscaler.model.RouterEntry;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsConsumer extends ConfiguredCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsConsumer.class);

    private final JedisUtil jedis;

    private static final int USE_MARK_EXPIRATION = 60 * 60 * 24;

    public StatsConsumer(final JedisUtil jedis) {
        super("consume_stats", "Consume stats from input queue");
        this.jedis = jedis;
    }

    @Override
    protected void run(final Bootstrap<StatsDrainConfiguration> bootstrap,
                       final Namespace namespace,
                       final StatsDrainConfiguration configuration) throws Exception {
        new QueueConsumer(configuration.statsQueue(), message -> {
            final RouterEntries entries = JsonUtil.fromJson(message.getPayload(), RouterEntries.class);
            LOGGER.info("Entries consumed {}", entries);
            try(Jedis jedis = this.jedis.nonTx()) {
                if(jedis.get(processedFrameId(entries)) == null) {
                    jedis.setex(processedFrameId(entries), USE_MARK_EXPIRATION, "true");
                    entries.getEntries().forEach(e -> {
                        final Long count = jedis.incr(counterId(entries.getAppName(), e));
                        final String avgServiceTimeId = avgServiceTimeId(entries.getAppName(), e);
                        final Double avgSoFar = Optional.ofNullable(jedis.get(avgServiceTimeId))
                                .map(Double::parseDouble)
                                .orElse(0.0d);
                        final int serviceTime = e.getMessage().getServiceMs() + e.getMessage().getConnectMs();
                        final Double newAvg = (avgSoFar * (count - 1) + serviceTime) / count;
                        jedis.setex(avgServiceTimeId, USE_MARK_EXPIRATION, newAvg.toString());
                    });
                }
                else {
                    LOGGER.info("Entries already in redis {}", entries);
                }
            }
            return true;
        }).run();
    }

    private String counterId(final String appName, final RouterEntry e) {
        final long epochSecond = e.getTimestamp().getEpochSecond();
        return appName + "-requests-cummulated-counter-" + (epochSecond / 10);
    }

    private String avgServiceTimeId(final String appName, final RouterEntry e) {
        final long epochSecond = e.getTimestamp().getEpochSecond();
        return appName + "-average-service-time-" + (epochSecond / 10);
    }

    private String processedFrameId(final RouterEntries entry) {
        return "frame-processed-" + entry.getFrameId();
    }
}
