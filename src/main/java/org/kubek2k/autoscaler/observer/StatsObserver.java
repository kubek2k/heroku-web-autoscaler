package org.kubek2k.autoscaler.observer;

import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import plan3.pure.redis.JedisUtil;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.librato.PoorMansLibrato;
import org.kubek2k.autoscaler.web.StatsDrainConfiguration;
import org.kubek2k.autoscaler.web.StatsDrainService;

public class StatsObserver extends EnvironmentCommand<StatsDrainConfiguration> {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private final PoorMansLibrato librato = new PoorMansLibrato("heroku.web.autoscaler");
    private final JedisUtil jedis;
    private final Double targetAverageServiceTime;

    public StatsObserver(final StatsDrainService service,
                         final JedisUtil jedis,
                         final Double targetAverageServiceTime) {
        super(service, "observe", "Observes stats and reacts");
        this.jedis = jedis;
        this.targetAverageServiceTime = targetAverageServiceTime;
    }

    @Override
    protected void run(final Environment environment,
                       final Namespace namespace,
                       final StatsDrainConfiguration configuration) throws Exception {
        final List<String> appNames = configuration.appNames();
        for(final String appName : appNames) {
            final Heroku heroku = configuration.heroku(environment);
            final TimePeriodStatsCache timePeriodStatsCache = new TimePeriodStatsCache(this.jedis);
            timePeriodStatsCache.prefill(appName, heroku.getNumberOfWebDynos(appName));
            this.executorService.scheduleAtFixedRate(new ScalingTask(appName,
                    heroku,
                    this.targetAverageServiceTime, this.librato, timePeriodStatsCache), 0, Granularity.GRANULARITY, TimeUnit.SECONDS).get();
            this.executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS);
        }
    }
}
