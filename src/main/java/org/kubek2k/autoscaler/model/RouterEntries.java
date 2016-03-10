package org.kubek2k.autoscaler.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RouterEntries {

    private final String frameId;
    private final List<RouterEntry> entries;

    @JsonCreator
    public RouterEntries(@JsonProperty("frameId") final String frameId,
                         @JsonProperty("entries") final List<RouterEntry> entries) {
        this.frameId = frameId;
        this.entries = entries;
    }

    @Override
    public String toString() {
        return "RouterEntries{" +
                "batchId='" + this.frameId + '\'' +
                ", entries=" + this.entries +
                '}';
    }
}
