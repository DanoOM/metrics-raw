package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;

public class RawTimer extends MetricBase implements Timer{
	protected Long startTime;
	private final boolean useStartTimeAsEventTime;

	RawTimer(String name, MetricRegistry registry, boolean useStartTimeAsEventTime) {
        super(name, registry, null);
        this.useStartTimeAsEventTime = useStartTimeAsEventTime;

    }

    RawTimer(String name, MetricRegistry registry, Map<String,String> customTags, boolean useStartTimeAsEventTime) {
        super(name, registry, customTags);
        this.useStartTimeAsEventTime = useStartTimeAsEventTime;
    }

    /** 'resets' the startTime to Now.
     *   Users can 'reuse' the same timer multple times, by invoking
     *   start/stop/start/stop..
     *   Timer Objects are NOT thread safe, and should not be used concurrently.
     * */
    @Override
    public Timer start() {
    	startTime = System.currentTimeMillis();
    	return this;
    }

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    @Override
    public Timer addTag(String name, String value) {
        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.put(name,value);
        return this;
    }

    /** calculates the time from the starttime, and triggers an event for the Listeners */
    @Override
    public long stop() {
    	return stop(System.currentTimeMillis() - startTime);
    }

    private long stop(long duration) {
        registry.postEvent(name,
                           useStartTimeAsEventTime ? startTime : startTime+duration,
                           tags,
                           duration);
        return duration;
    }

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    @Override
    public long stop(String... tags) {
    	return stop(Util.buildTags(tags));
    }

    /** todo This should error out, or 'not' update the duration on an already stopped timer. */
    @Override
    public long stop(Map<String,String> customTags) {
        long duration = System.currentTimeMillis() - startTime;
    	if (this.tags == null) {
    	    this.tags = new HashMap<>();
    	}
    	this.tags.putAll(customTags);
    	return stop(duration);
    }
}
