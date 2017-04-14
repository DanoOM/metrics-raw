package org.dshops.metrics;

import java.util.Map;

public class DoubleEvent extends EventImpl {
    double value;
    public DoubleEvent(final String name, final Map<String,String> tags, final long time, double value) {
        super(name,tags,time);
        this.value = value;
    }

    @Override
    public String toString() {
        return super.toString()  + " " + value;
    }

    @Override
	public long getLongValue(){
    	return (long)value;
    }

    @Override
    public double getDoubleValue() {
    	return value;
    }
}
