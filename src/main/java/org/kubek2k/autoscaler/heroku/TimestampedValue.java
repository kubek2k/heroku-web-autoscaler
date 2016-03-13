package org.kubek2k.autoscaler.heroku;

class TimestampedValue<T> {
    private final long timestamp;

    private final T val;

    public TimestampedValue(final long timestamp, final T val) {
        this.timestamp = timestamp;
        this.val = val;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public T getVal() {
        return this.val;
    }
}
