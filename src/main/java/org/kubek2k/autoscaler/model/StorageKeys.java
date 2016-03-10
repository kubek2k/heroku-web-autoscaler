package org.kubek2k.autoscaler.model;

public class StorageKeys {

    public static String counterId(final String appName, final long epochSecond) {
        return appName + "-requests-cummulated-counter-" + (epochSecond / 10);
    }

    public static String avgServiceTimeId(final String appName, final long epochSecond) {
        return appName + "-average-service-time-" + (epochSecond / 10);
    }

    public static String processedFrameId(final RouterEntries entry) {
        return "frame-processed-" + entry.getFrameId();
    }
}
