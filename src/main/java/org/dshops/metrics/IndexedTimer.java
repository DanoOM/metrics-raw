package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexedTimer extends MetricBase {
	protected Long startTime;
	protected long duration;
	private String primaryTag;

	// map of 'collisions' for metrics within the same millis.
	private static Map<String,Map<Long,AtomicInteger>> collisions = new ConcurrentHashMap(){};

    IndexedTimer(String name, MetricRegistry registry) {
        super(name, registry, null);
    }

    IndexedTimer(String name, MetricRegistry registry, Map<String,String> customTags) {
        super(name, registry, customTags);
    }

    /** Returns a new timer, with startTime = now */
    IndexedTimer start() {
    	startTime = System.currentTimeMillis();
    	Map<Long,AtomicInteger> indexes = collisions.get(name);
    	AtomicInteger index = indexes.get(startTime);
    	if (index == null) {
    	    synchronized (index) {
                index = indexes.get(startTime);
                if (index == null) {
                    indexes.put(startTime, new AtomicInteger());
                }
            }
    	}
    	index.incrementAndGet();
    	return this;
    }

    /** calculates the time from the starttime, also triggers an event for Listeners */
    public long stop() {
        if (duration != 0) {
            // already stopped
            return duration;
        }
    	duration = System.currentTimeMillis() - startTime;
    	registry.postEvent(name, startTime, tags, duration, EventType.Timer);
    	return duration;
    }

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    public long stop(String... tags) {
    	return stop(Util.buildTags(tags));
    }

    /** todo This should error out, or 'not' update the duration on an already stopped timer. */
    public long stop(Map<String,String> customTags) {
        if (duration != 0) {
            // already stopped
            return duration;
        }
    	duration = System.currentTimeMillis() - startTime;
    	if (this.tags == null) {
    	    this.tags = new HashMap<>();
    	}
    	this.tags.putAll(customTags);
    	AtomicInteger index = collisions.get(name).get(startTime);
    	int idx = index.decrementAndGet();
    	customTags.put("index", idx + "");
    	registry.postEvent(name, startTime, this.tags, duration, EventType.Timer);
    	return duration;
    }

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    public IndexedTimer addTag(String name, String value) {
        if (this.tags == null){
            this.tags = new HashMap<>();
        }
        this.tags.put(name,value);
        return this;
    }

    public static class Builder {
    	private IndexedTimer timer;
        Builder(String name, MetricRegistry registry) {
            this.timer = new IndexedTimer(name, registry, new HashMap<>());
        }

        public Builder addTag(String name, String value) {
            timer.tags.put(name,value);
            return this;
        }

        public IndexedTimer build() {
        	return timer.start();
        }
    }
}
