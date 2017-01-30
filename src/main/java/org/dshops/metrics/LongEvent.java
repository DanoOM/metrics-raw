package org.dshops.metrics;

import java.util.Map;

public class LongEvent extends EventImpl {
    long value;
    LongEvent(final String name, final Map<String,String> tags, EventType type, final long time, long value) {
        super(name,tags,type, time);
        this.value = value;
    }

    @Override
    public String toString() {
        return super.toString()  + " " + value;
    }

    @Override
	public long getLongValue() {
    	return value;
    }

    @Override
	public double getDoubleValue() {
    	return value;
    }
}