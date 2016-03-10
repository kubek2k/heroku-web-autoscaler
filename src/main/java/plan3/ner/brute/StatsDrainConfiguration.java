package plan3.ner.brute;

import static java.util.Arrays.asList;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import plan3.pure.config.Env;
import plan3.restin.dw.Plan3DwConfiguration;
import plan3.restin.jersey.filters.ApiKeyFilter;
import plan3.restin.jersey.filters.ForceHttpsFilter;
import plan3.restin.jersey.filters.IgnoredResourcesFilter;

public class StatsDrainConfiguration extends Configuration implements Plan3DwConfiguration {

    private final Env env;
    private final String serviceApiKey;

    public StatsDrainConfiguration() {
        this.env = new Env(System.getenv());
        this.serviceApiKey = this.env.required("API_KEY");
    }

    public Iterable<?> jerseyFilters() {
        return asList(new ForceHttpsFilter(this.env),
                new IgnoredResourcesFilter(),
                new ApiKeyFilter(this.serviceApiKey, "*"));
    }

    public Iterable<String> corsHttpVerbs() {
        return asList("PUT", "POST", "DELETE");
    }

    public boolean usesAuthentication() {
        return false;
    }

    public void registerResource(final Environment env) {
        env.jersey().register(new StatsDrainResource());
    }
}
