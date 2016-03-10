package org.kubek2k.autoscaler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RouterStats {
    private final String host;
    private final String method;
    private final String path;
    private final int statusCode;
    private final int connectMs;
    private final int serviceMs;

    @JsonCreator
    public RouterStats(@JsonProperty("host") final String host,
                       @JsonProperty("method") final String method,
                       @JsonProperty("path") final String path,
                       @JsonProperty("statusCode") final  int statusCode,
                       @JsonProperty("connectMs") final int connectMs,
                       @JsonProperty("serviceMs") final int serviceMs) {
        this.host = host;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.connectMs = connectMs;
        this.serviceMs = serviceMs;
    }

    @Override
    public String toString() {
        return "RouterStats{" +
                "host='" + this.host + '\'' +
                ", method='" + this.method + '\'' +
                ", path='" + this.path + '\'' +
                ", statusCode=" + this.statusCode +
                ", connectMs=" + this.connectMs +
                ", serviceMs=" + this.serviceMs +
                '}';
    }
}
