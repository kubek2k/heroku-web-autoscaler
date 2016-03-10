package plan3.ner.brute;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import plan3.pure.config.Env;
import plan3.pure.logging.LogConfigurer;
import plan3.restin.dw.Plan3Bundle;

public class StatsDrainService extends Application<StatsDrainConfiguration> {

    @Override
    public void initialize(final Bootstrap<StatsDrainConfiguration> bootstrap) {
        final Env env = new Env(System.getenv());
        bootstrap.addBundle(new Plan3Bundle(env));
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
