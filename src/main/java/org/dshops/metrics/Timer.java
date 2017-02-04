package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;

public class Timer extends MetricBase {
	protected long startTime;
	private final boolean useStartTimeAsEventTime;

    Timer(String name, MetricRegistry registry, boolean useStartTimeAsEventTime) {
        super(name, registry, null);
        this.useStartTimeAsEventTime = useStartTimeAsEventTime;

    }

    Timer(String name, MetricRegistry registry, Map<String,String> customTags, boolean useStartTimeAsEventTime) {
        super(name, registry, customTags);
        this.useStartTimeAsEventTime = useStartTimeAsEventTime;
    }

    /** Returns a new timer, with startTime = now */
    Timer start() {
    	startTime = System.currentTimeMillis();
    	return this;
    }

    /** calculates the time from the starttime, also triggers an event for Listeners */
    public long stop() {
    	long duration = System.currentTimeMillis() - startTime;
    	registry.postEvent(name,
    	                   useStartTimeAsEventTime ? startTime : startTime+duration,
    	                   tags,
    	                   duration,
    	                   EventType.Timer);
    	return duration;
    }

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    public long stop(String... tags) {
    	return stop(Util.buildTags(tags));
    }

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    public Timer addTag(String name, String value) {
        if (this.tags == null){
            this.tags = new HashMap<>();
        }
        this.tags.put(name,value);
        return this;
    }

    /** todo This should error out, or 'not' update the duration on an already stopped timer. */
    public long stop(Map<String,String> customTags) {
    	long duration = System.currentTimeMillis() - startTime;
    	if (this.tags == null){
    	    this.tags = new HashMap<>();
    	}
    	this.tags.putAll(customTags);
    	registry.postEvent(name,
    	                   useStartTimeAsEventTime ? startTime : startTime+duration,
    	                   this.tags,
    	                   duration,
    	                   EventType.Timer);
    	return duration;
    }

    public static class Builder {
    	private Timer timer;
        Builder(String name, MetricRegistry registry, boolean useStartTimeAsEventTime) {
            this.timer = new Timer(name, registry, new HashMap<>(), useStartTimeAsEventTime);
        }

        public Builder addTag(String name, String value) {
            timer.tags.put(name,value);
            return this;
        }

        public Timer build() {
        	return timer.start();
        }
    }
}
