package org.dshops.metrics;

import java.util.concurrent.atomic.LongAdder;

/** A Gauage that can be scheduled, backed by a counter.
 * the counter will be re-created once the gauage reports its results.
 *
 * */
public class MeterImpl implements Gauge<Number>, Meter {
    private volatile LongAdder adder;

    public MeterImpl() {
        adder = new LongAdder();
    }

    @Override
    public Number getValue() {
        Number result = adder.intValue();
        adder = new LongAdder();
        return result;
    }

    /** mark represents the occurs of an event. */
    @Override
    public void mark() {
        adder.increment();
    }
}
