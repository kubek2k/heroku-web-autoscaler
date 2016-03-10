package org.kubek2k.autoscaler.web;

import static java.util.Arrays.asList;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import plan3.msg.queue.Queue;
import plan3.msg.sqs.SQSClient;
import plan3.pure.config.Env;
import plan3.restin.dw.Plan3DwConfiguration;
import plan3.restin.jersey.filters.ApiKeyFilter;
import plan3.restin.jersey.filters.ForceHttpsFilter;
import plan3.restin.jersey.filters.IgnoredResourcesFilter;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StatsDrainConfiguration extends Configuration implements Plan3DwConfiguration {

    private final Env env;
    private final String serviceApiKey;

    public StatsDrainConfiguration() {
        this.env = new Env(System.getenv());
        this.serviceApiKey = this.env.required("API_KEY");
    }

    @Override
    public Iterable<?> jerseyFilters() {
        return asList(new ForceHttpsFilter(this.env),
                new IgnoredResourcesFilter(),
                new ApiKeyFilter(this.serviceApiKey, "*"));
    }

    @Override
    public Iterable<String> corsHttpVerbs() {
        return asList("PUT", "POST", "DELETE");
    }

    @Override
    public boolean usesAuthentication() {
        return false;
    }

    private static final Pattern CSV_PATTERN = Pattern.compile(",");

    public void registerResource(final Environment env) {
        final List<String> disabledPaths = this.env.optional("DISABLED_PATHS")
                .map(CSV_PATTERN::splitAsStream)
                .map(s -> s.collect(Collectors.toList()))
                .orElse(Collections.emptyList());
        env.jersey().register(new StatsDrainResource(statsQueue(), disabledPaths));
    }

    public Queue statsQueue() {
        return new SQSClient().queue(this.env.required("STATS_QUEUE_URL"));
    }
}
