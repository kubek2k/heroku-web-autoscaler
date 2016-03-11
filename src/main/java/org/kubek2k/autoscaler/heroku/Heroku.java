package org.kubek2k.autoscaler.heroku;

import java.util.Map;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

public class Heroku {

    private final WebTarget herokuWebTarget;

    private final String apiKey;

    public Heroku(final WebTarget herokuWebTarget, final String apiKey) {
        this.herokuWebTarget = herokuWebTarget;
        this.apiKey = apiKey;
    }

    public int getNumberOfWebDynos(final String app) {
        final Map<String, Object> result = this.herokuWebTarget
                .path("apps")
                .path(app)
                .path("formation")
                .path("web")
                .request()
                .accept("application/vnd.heroku+json; version=3")
                .header("Authorization", "Bearer " + this.apiKey)
                .buildGet()
                .invoke(new GenericType<Map<String, Object>>() {
                });
        return Integer.parseInt(result.get("quantity").toString());
    }
}
