package org.kubek2k.autoscaler.statsconsumer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.msg.queue.QueueConsumer;
import plan3.pure.redis.JedisUtil;
import plan3.pure.redis.Tx;
import plan3.restin.jackson.JsonUtil;

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
            LOGGER.info("Entry consumed {}", entries);
            try(Tx tx = this.jedis.tx()) {
                if(tx.redis().get(processedFrameId(entries)) == null) {
                    tx.redis().setex(processedFrameId(entries), USE_MARK_EXPIRATION, "true");
                    entries.getEntries().forEach(e -> {
                        final Long count = tx.redis().incr(counterId(e)).get();
                        final String avgServiceTimeId = avgServiceTimeId(e);
                        final Float avgSoFar = Optional.ofNullable(tx.redis().get(avgServiceTimeId).get())
                                .map(Float::parseFloat)
                                .orElse(0.0f);
                        final int serviceTime = e.getMessage().getServiceMs() + e.getMessage().getConnectMs();
                        final Float newAvg = (avgSoFar * (count - 1) + serviceTime) / count;
                        tx.redis().setex(avgServiceTimeId, USE_MARK_EXPIRATION, newAvg.toString());
                    });
                }
            }
            return true;
        }).run();
    }

    private String counterId(final RouterEntry e) {
        final long epochSecond = e.getTimestamp().getEpochSecond();
        return "requests-cummulated-counter-" + (epochSecond / 10);
    }

    private String avgServiceTimeId(final RouterEntry e) {
        final long epochSecond = e.getTimestamp().getEpochSecond();
        return "average-service-time-" + (epochSecond / 10);
    }

    private String processedFrameId(final RouterEntries entry) {
        return "frame-processed-" + entry.getFrameId();
    }
}
