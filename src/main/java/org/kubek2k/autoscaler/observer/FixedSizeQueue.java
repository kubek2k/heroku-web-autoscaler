package org.kubek2k.autoscaler.observer;

import java.util.Deque;
import java.util.LinkedList;
import java.util.stream.Stream;

public class FixedSizeQueue<T> {

    private final Deque<T> internal = new LinkedList<>();
    private final int maxSize;
    private final Object sync = new Object();

    public FixedSizeQueue(final int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(final T o) {
        synchronized(this.sync) {
            this.internal.add(o);
            if(this.internal.size() > this.maxSize) {
                this.internal.removeLast();
            }
        }
    }

    public Stream<T> currently() {
        return this.internal.stream();
    }

    public int size() {
        return this.internal.size();
    }
}
