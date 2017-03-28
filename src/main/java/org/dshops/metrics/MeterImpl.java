package org.dshops.metrics;

import java.util.concurrent.atomic.LongAdder;

/** A Gauge that can be scheduled, backed by a counter.
 * the counter will be re-created once the gauge reports its results.
 *
 * */
public class MeterImpl implements Gauge<Number>, Meter {
    private volatile LongAdder adder;

    public MeterImpl() {
        adder = new LongAdder();
    }

    @Override
    public Number getValue() {
        LongAdder tmp = adder;
        adder = new LongAdder();
        return tmp.intValue();
    }

    /** mark represents the occurs of an event. */
    @Override
    public void mark() {
        adder.increment();
    }

    @Override
    public void mark(int incrementBy) {
        adder.add(incrementBy);
    }
}
