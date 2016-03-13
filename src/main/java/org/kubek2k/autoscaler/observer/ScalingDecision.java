package org.kubek2k.autoscaler.observer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalingDecision {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScalingDecision.class);

    private final Map<String, FixedSizeQueue<Integer>> dynoCountMemory = new ConcurrentHashMap<>();

    private final int minDynoCount = 1;

    private final int maxDynoCount = 16;

    public boolean shouldIScale(final String appName, final int currentDynoCount, final int newDynoCount) {
        if (currentDynoCount != newDynoCount) {
            if (newDynoCount >= this.minDynoCount && newDynoCount <= this.maxDynoCount) {
                final FixedSizeQueue<Integer> appMemory = getOrCreateNewOne(appName);
                appMemory.add(currentDynoCount);
                if(hasEnoughKnowledge(appMemory)) {
                    if(scalingUp(currentDynoCount, newDynoCount)) {
                        return shouldScaleUp(appMemory);
                    }
                    else {
                        return shouldScaleDown(appMemory);
                    }
                }
                LOGGER.info("Not enough information in memory to perform scaling decision");
                return false;
            } else {
                LOGGER.warn("Suggested dyno count: {} not with range {}-{}",
                        newDynoCount,
                        this.minDynoCount,
                        this.maxDynoCount);
                return false;
            }
        } else {
            LOGGER.info("No need to scale. Both values the same");
            return false;
        }
    }

    private boolean shouldScaleUp(final FixedSizeQueue<Integer> appMemory) {
        return appMemory.currently()
                .limit(6)
                .distinct()
                .count() == 1;
    }

    private boolean shouldScaleDown(final FixedSizeQueue<Integer> appMemory) {
        return appMemory.currently()
                .limit(60)
                .distinct()
                .count() == 1;
    }

    private boolean scalingUp(final int currentDynoCount, final int newDynoCount) {
        return currentDynoCount < newDynoCount;
    }

    private static boolean hasEnoughKnowledge(final FixedSizeQueue<Integer> appMemory) {
        return appMemory.size() == 60;
    }

    private FixedSizeQueue<Integer> getOrCreateNewOne(final String appName) {
        FixedSizeQueue<Integer> appMemory = this.dynoCountMemory.get(appName);
        if (appMemory == null) {
            appMemory = new FixedSizeQueue<>(60);
            this.dynoCountMemory.put(appName, appMemory);
        }
        return appMemory;
    }
}
