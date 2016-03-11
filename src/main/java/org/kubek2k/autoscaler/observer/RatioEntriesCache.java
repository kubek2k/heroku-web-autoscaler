package org.kubek2k.autoscaler.observer;

import plan3.ner.brute.model.RatioEntry;

import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.model.TimeStats;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultiset;

public class RatioEntriesCache {

    private final Deque<RatioEntry> ratioEntries = new LinkedList<>();

    public void add(final RatioEntry ratioEntry) {
        this.ratioEntries.add(ratioEntry);
    }

    public TimeStats aggregateBack(final int lookbackWindowSize) {
        final List<TimeStats> lastMinuteStats = this.ratioEntries.stream()
                .limit(lookbackWindowSize / Granularity.GRANULARITY)
                .map(RatioEntry::getTimeStats)
                .collect(Collectors.toList());
        final int aggregatedHitCount = lastMinuteStats.stream()
                .map(t -> t.hitCount)
                .reduce((c1, c2) -> c1 + c2)
                .get();
        final double aggregatedAvgServiceTime = lastMinuteStats.stream()
                .map(t -> t.avgServiceTime * t.hitCount)
                .reduce((t1, t2) -> t1 + t2)
                .get() / aggregatedHitCount;
        return new TimeStats(aggregatedAvgServiceTime, aggregatedHitCount);
    }

    public double countRatioMedian() {
        final TreeMultiset<RatioEntry> medianFinder = TreeMultiset.create(new Comparator<RatioEntry>() {
            @Override
            public int compare(final RatioEntry o1, final RatioEntry o2) {
                return o1.getRatio() - o2.getRatio() > 0 ? 1 : -1;
            }
        });
        medianFinder.addAll(this.ratioEntries);
        return Iterables.get(medianFinder, medianFinder.size() / 2).getRatio();
    }

    public void addNewRatioEntry(final long lastObservation,
                                  final TimeStats mostRecentStats,
                                  final int currentDynoCount) {
        this.ratioEntries.removeLast();
        this.ratioEntries.addFirst(new RatioEntry(lastObservation, currentDynoCount, mostRecentStats, Granularity.GRANULARITY));
    }
}
