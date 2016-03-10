package org.kubek2k.autoscaler.statsconsumer;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.msg.queue.QueueConsumer;
import plan3.pure.redis.JedisUtil;
import plan3.restin.jackson.JsonUtil;

import org.kubek2k.autoscaler.model.RouterEntry;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsConsumer extends ConfiguredCommand<StatsDrainConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsConsumer.class);

    private final JedisUtil jedis;

    public StatsConsumer(final JedisUtil jedis) {
        super("consume_stats", "Consume stats from input queue");
        this.jedis = jedis;
    }

    @Override
    protected void run(final Bootstrap<StatsDrainConfiguration> bootstrap,
                       final Namespace namespace,
                       final StatsDrainConfiguration configuration) throws Exception {
        new QueueConsumer(configuration.statsQueue(), message -> {
            final RouterEntry entry = JsonUtil.fromJson(message.getPayload(), RouterEntry.class);
            LOGGER.info("Entry consumed {}", entry);
            return false;
        }).run();
    }
}
