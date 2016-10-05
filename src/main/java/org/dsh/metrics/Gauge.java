package org.dsh.metrics;

@FunctionalInterface
public interface Gauge<T> {
    T getValue();
}
