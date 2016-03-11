package org.kubek2k.autoscaler.model;

import org.kubek2k.autoscaler.Granularity;

public class StorageKeys {

    public static String counterId(final String appName, final long epochSecond) {
        return appName + "-requests-cummulated-counter-" + (epochSecond / Granularity.GRANULARITY);
    }

    public static String avgServiceTimeId(final String appName, final long epochSecond) {
        return appName + "-average-service-time-" + (epochSecond / Granularity.GRANULARITY);
    }

    public static String numberOfDynosId(final String appName, final long epochSecond) {
        return appName + "-numer-of-dynos-" + (epochSecond / Granularity.GRANULARITY);
    }

    public static String processedFrameId(final RouterEntries entry) {
        return "frame-processed-" + entry.getFrameId();
    }
}
