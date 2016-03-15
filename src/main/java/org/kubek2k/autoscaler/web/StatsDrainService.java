package org.kubek2k.autoscaler.web;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import plan3.pure.config.Env;
import plan3.pure.logging.LogConfigurer;
import plan3.pure.redis.JedisUtil;
import plan3.restin.dw.Plan3Bundle;

import org.kubek2k.autoscaler.observer.StatsObserver;
import org.kubek2k.autoscaler.statsconsumer.StatsConsumer;

public class StatsDrainService extends Application<StatsDrainConfiguration> {

    @Override
    public void initialize(final Bootstrap<StatsDrainConfiguration> bootstrap) {
        final Env env = new Env(System.getenv());
        bootstrap.addBundle(new Plan3Bundle(env));
        final JedisUtil jedis = new JedisUtil(env.required("REDIS_URL"));
        bootstrap.addCommand(new StatsConsumer(jedis));
        final Double targetAverageServiceTime = env.optional("TARGET_AVERAGE_SERVICE_TIME")
                .map(Double::parseDouble)
                .orElse(250.0);
        bootstrap.addCommand(new StatsObserver(this, jedis, targetAverageServiceTime));
        LogConfigurer.configure(env.optional("LOGGING"));
    }

    @Override
    public void run(final StatsDrainConfiguration config, final Environment env) throws Exception {
        config.registerResource(env);
    }

    public static void main(final String[] args) throws Exception {
        new StatsDrainService().run(args);
    }
}
