package org.kubek2k.autoscaler.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RouterEntries {

    private final String frameId;
    private final List<RouterEntry> entries;
    private final String appName;

    @JsonCreator
    public RouterEntries(@JsonProperty("frameId") final String frameId,
                         @JsonProperty("entries") final List<RouterEntry> entries, final String appName) {
        this.frameId = frameId;
        this.entries = entries;
        this.appName = appName;
    }

    public String getFrameId() {
        return this.frameId;
    }

    public List<RouterEntry> getEntries() {
        return this.entries;
    }

    public String getAppName() {
        return this.appName;
    }
}
