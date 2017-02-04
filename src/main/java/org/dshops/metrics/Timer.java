package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;

public class Timer extends MetricBase {
	protected Long startTime;
	private Long duration;


    Timer(String name, MetricRegistry registry) {
        super(name, null, registry, null);
    }

    Timer(String name, MetricRegistry registry, Map<String,String> customTags) {
        super(name, null, registry, customTags);
    }

    Timer(String name,
          String primaryTag,
          MetricRegistry registry,
          Map<String,String> customTags) {
        super(name, primaryTag, registry, customTags);
    }

    /** Returns a new timer, with startTime = now */
    Timer start() {
    	startTime = System.currentTimeMillis();
    	return this;
    }

    /** calculates the time from the starttime, also triggers an event for Listeners */
    public long stop() {
        if (duration != null) return duration;
    	duration = System.currentTimeMillis() - startTime;
    	registry.postEvent(name, primaryTag, startTime, tags, duration);
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
        if (duration != null) return duration;
    	duration = System.currentTimeMillis() - startTime;
    	if (this.tags == null){
    	    this.tags = new HashMap<>();
    	}
    	this.tags.putAll(customTags);
    	// note: we should pass in endTime here.. if using kairosdb timeslots..
    	registry.postEvent(name, primaryTag, startTime, this.tags, duration);
    	return duration;
    }

    public static class Builder {
    	private Timer timer;
        Builder(String name, MetricRegistry registry) {
            this.timer = new Timer(name, registry, new HashMap<>());
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
