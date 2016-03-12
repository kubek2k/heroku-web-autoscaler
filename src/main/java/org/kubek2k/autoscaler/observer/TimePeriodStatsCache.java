package org.kubek2k.autoscaler.observer;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.kubek2k.autoscaler.Granularity;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultiset;

public class TimePeriodStatsCache {

    private final Deque<TimePeriodStats> ratioEntries = new LinkedList<>();

    public void add(final TimePeriodStats ratioEntry) {
        this.ratioEntries.add(ratioEntry);
    }

    public TimePeriodStats aggregateBack(final int lookbackWindowSize) {
        final List<TimePeriodStats> lastMinuteStats = this.ratioEntries.stream()
                .limit(lookbackWindowSize / Granularity.GRANULARITY)
                .collect(Collectors.toList());
        return lastMinuteStats.stream()
                .reduce(TimePeriodStats::aggregate)
                .get();
    }

    public Optional<Double> countRatioMedian() {
        final TreeMultiset<Double> ratios = this.ratioEntries.stream()
                .map(TimePeriodStats::getRatio)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(TreeMultiset::create));
        if (!ratios.isEmpty()) {
            return Optional.of(Iterables.get(ratios, ratios.size() / 2));
        } else {
            return Optional.empty();
        }
    }

    public void addNewRatioEntry(final TimePeriodStats mostRecentStats) {
        this.ratioEntries.removeLast();
        this.ratioEntries.addFirst(mostRecentStats);
    }

    @Override
    public String toString() {
        return "RatioEntriesCache{" +
                "ratioEntries=" + this.ratioEntries +
                '}';
    }
}
