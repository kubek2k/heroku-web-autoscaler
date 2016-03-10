package org.kubek2k.autoscaler.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RouterEntry {
    private final Instant timestamp;

    private final RouterStats message;

    @JsonCreator
    public RouterEntry(@JsonProperty("timestamp") Instant timestamp,
                       @JsonProperty("message") RouterStats message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    @Override
    public String toString() {
        return "RouterEntry{" +
                "timestamp=" + this.timestamp +
                ", routerStats='" + this.message + '\'' +
                '}';
    }
}
