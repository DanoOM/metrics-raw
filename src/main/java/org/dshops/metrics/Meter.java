package org.dshops.metrics;

public interface Meter {
    public void mark();
    public void mark(int incrementBy);
}
