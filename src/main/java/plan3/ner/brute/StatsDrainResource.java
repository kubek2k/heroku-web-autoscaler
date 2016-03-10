package plan3.ner.brute;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/drain")
public class StatsDrainResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsDrainResource.class);

    @POST
    @Consumes("application/logplex-1")
    public void consumeBatch(@QueryParam("app") final String appName,
                             @Context final UriInfo uriInfo,
                             final String logs) {
        LOGGER.info("Got some logs {}", logs);
    }
}
