package org.dshops.metrics;

@FunctionalInterface
public interface Gauge<T> {
    T getValue();
}
