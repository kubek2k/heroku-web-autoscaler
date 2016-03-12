package org.kubek2k.autoscaler.observer;

import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.kubek2k.autoscaler.Granularity;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultiset;

public class RatioEntriesCache {

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

    public double countRatioMedian() {
        final TreeMultiset<TimePeriodStats> medianFinder = TreeMultiset.create(new Comparator<TimePeriodStats>() {
            @Override
            public int compare(final TimePeriodStats o1, final TimePeriodStats o2) {
                return o1.getRatio() - o2.getRatio() > 0 ? 1 : -1;
            }
        });
        medianFinder.addAll(this.ratioEntries);
        return Iterables.get(medianFinder, medianFinder.size() / 2).getRatio();
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
