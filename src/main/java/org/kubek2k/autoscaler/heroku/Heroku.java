package org.kubek2k.autoscaler.heroku;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class Heroku {

    private final WebTarget herokuWebTarget;
    private final String apiKey;
    private final Cache<String, Integer> cache;

    public Heroku(final WebTarget herokuWebTarget, final String apiKey) {
        this.herokuWebTarget = herokuWebTarget;
        this.apiKey = apiKey;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    public int getNumberOfWebDynos(final String app) throws ExecutionException {
        return this.cache.get(app, () -> {
            final Map<String, Object> result = this.herokuWebTarget
                    .path("apps")
                    .path(app)
                    .path("formation")
                    .path("web")
                    .request()
                    .accept("application/vnd.heroku+json; version=3")
                    .header("Authorization", "Bearer " + this.apiKey)
                    .buildGet()
                    .invoke(new GenericType<Map<String, Object>>() {});
            return Integer.parseInt(result.get("quantity").toString());
        });
    }
}
