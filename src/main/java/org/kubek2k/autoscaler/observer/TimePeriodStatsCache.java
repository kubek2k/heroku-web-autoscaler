package org.kubek2k.autoscaler.observer;

import plan3.pure.redis.JedisUtil;
import plan3.pure.redis.Tx;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.kubek2k.autoscaler.Granularity;
import org.kubek2k.autoscaler.heroku.Heroku;
import org.kubek2k.autoscaler.model.StorageKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultiset;

public class TimePeriodStatsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimePeriodStatsCache.class);
    private static final int RATIO_CACHE_SIZE = 50;

    private final Deque<TimePeriodStats> timePeriodStats = new LinkedList<>();

    private final JedisUtil jedis;

    public TimePeriodStatsCache(final JedisUtil jedis) {
        this.jedis = jedis;
    }

    public void add(final TimePeriodStats stat) {
        this.timePeriodStats.add(stat);
    }

    public TimePeriodStats aggregateBack(final int lookbackWindowSize) {
        final List<TimePeriodStats> lastMinuteStats = this.timePeriodStats.stream()
                .limit(lookbackWindowSize / Granularity.GRANULARITY)
                .collect(Collectors.toList());
        return lastMinuteStats.stream()
                .reduce(TimePeriodStats::aggregate)
                .get();
    }

    public Optional<Double> countRatioMedian() {
        final TreeMultiset<Double> ratios = this.timePeriodStats.stream()
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

    public void addStats(final TimePeriodStats mostRecentStats) {
        this.timePeriodStats.removeLast();
        this.timePeriodStats.addFirst(mostRecentStats);
    }

    public void prefill(final String appName, final int initialDynoCount) {
        final long lastObservation = Instant.now().getEpochSecond() - 2 * Granularity.GRANULARITY;
        LOGGER.info("Prefilling cache");
        getTimeStatsInOneShot(appName, lastObservation)
                .stream()
                .map(pair -> {
                    final Long epochTimestamp = (Long) pair[0];
                    final Double avgServiceTime = extractAvgServiceTime(pair);
                    final Integer hitCount = extractHitCount(pair);
                    return new TimePeriodStats(epochTimestamp,
                            Granularity.GRANULARITY,
                            initialDynoCount,
                            avgServiceTime,
                            hitCount);
                })
                .forEach(this::add);
        LOGGER.info("Prefilling done {}", this);
    }

    private Integer extractHitCount(final Object[] responseArr) {
        return Optional.ofNullable(((Response<String>) responseArr[2]).get())
                .map(Integer::parseInt)
                .orElse(0);
    }

    private Double extractAvgServiceTime(final Object[] responseArr) {
        return Optional.ofNullable(((Response<String>) responseArr[1]).get())
                .map(Double::parseDouble)
                .orElse(0.0);
    }

    private List<Object[]> getTimeStatsInOneShot(final String appName, final long lastObservation) {
        try(final Tx tx = this.jedis.tx()) {
            return LongStream.iterate(lastObservation,
                    i -> i - Granularity.GRANULARITY)
                    .limit(RATIO_CACHE_SIZE)
                    .mapToObj(observation -> getTimeStatsResponses(appName, observation, tx.redis()))
                    .collect(Collectors.toList());
        }
    }

    private Object[] getTimeStatsResponses(final String appName, final long pointInTime, final Transaction tx) {
        final Response<String> avgServiceTime = tx.get(StorageKeys.avgServiceTimeId(appName, pointInTime));
        final Response<String> hitCount = tx.get(StorageKeys.counterId(appName, pointInTime));
        return new Object[]{pointInTime, avgServiceTime, hitCount};
    }

    public TimePeriodStats getTimePeriodStats(final String appName,
                                               final long pointInTime,
                                               final Heroku heroku) throws ExecutionException {
        final Object[] responses;
        try(final Tx tx = this.jedis.tx()) {
            responses = getTimeStatsResponses(appName, pointInTime, tx.redis());
        }
        final Double avgServiceTime = extractAvgServiceTime(responses);
        final Integer hitCount = extractHitCount(responses);
        return new TimePeriodStats(pointInTime,
                Granularity.GRANULARITY,
                heroku.getNumberOfWebDynos(appName),
                avgServiceTime,
                hitCount);
    }

    @Override
    public String toString() {
        return "TimePeriodStatsCache{" +
                "timePeriodStats=" + this.timePeriodStats +
                '}';
    }
}
