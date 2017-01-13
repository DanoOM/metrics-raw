package org.dshops;

@FunctionalInterface
public interface Gauge<T> {
    T getValue();
}
