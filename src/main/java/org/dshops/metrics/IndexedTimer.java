package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexedTimer extends Timer {
	protected long duration;
	private final String primaryTag;

	// map of 'collisions' for metrics within the same millis.
	private static Map<String,Map<Long,AtomicInteger>> collisions = new ConcurrentHashMap(){};

    IndexedTimer(String name, String primaryTag, MetricRegistry registry) {
        super(name, registry, null);
        if (!registry.getTags().containsKey(primaryTag)){
            throw new IllegalArgumentException("Illegal primaryTag, it must be present within the registry tag-set.");
        }
        this.primaryTag = primaryTag;
    }

    IndexedTimer(String name, String primaryTag, MetricRegistry registry, Map<String,String> customTags) {
        super(name, registry, customTags);

        if ( !(customTags.containsKey(primaryTag) || registry.getTags().containsKey(primaryTag)) ) {
            throw new IllegalArgumentException("Illegal primaryTag, it must be present within the registry tag-set, or provided customTags");
        }
        this.primaryTag = primaryTag;
    }

    /** Returns a new timer, with startTime = now */
    @Override
    IndexedTimer start() {
    	startTime = System.currentTimeMillis();
    	Map<Long,AtomicInteger> indexes = collisions.get(name + tags.get(primaryTag));
    	if (indexes == null) {
    	    synchronized (collisions) {
    	        indexes = collisions.get(name + tags.get(primaryTag));
    	        if (indexes == null) {
    	            indexes = new ConcurrentHashMap<>();
    	            collisions.put(name + tags.get(primaryTag), indexes);
    	        }
            }
    	}
    	AtomicInteger index = indexes.get(startTime);
    	if (index == null) {
    	    synchronized (indexes) {
                index = indexes.get(startTime);
                if (index == null) {
                    index = new AtomicInteger();
                    indexes.put(startTime, index);
                }
            }
    	}
    	index.incrementAndGet();
    	return this;
    }

    /** calculates the time from the starttime, also triggers an event for Listeners */
    @Override
    public long stop() {
        if (duration != 0) {
            // already stopped
            return duration;
        }
    	duration = System.currentTimeMillis() - startTime;
    	handleIndexTag(tags);
    	registry.postEvent(name, startTime, tags, duration, EventType.Timer);
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
        if (duration != 0) {
            // already stopped
            return duration;
        }
    	duration = System.currentTimeMillis() - startTime;
    	if (this.tags == null) {
    	    this.tags = new HashMap<>();
    	}
    	this.tags.putAll(customTags);
    	handleIndexTag(customTags);
    	registry.postEvent(name, startTime, this.tags, duration, EventType.Timer);
    	return duration;
    }

    private void handleIndexTag(Map<String, String> customTags) {
        try {
            Map<Long,AtomicInteger> indexes = collisions.get(name + tags.get(primaryTag));
            AtomicInteger index = indexes.get(startTime);
            int idx = index.decrementAndGet();
            customTags.put("index", idx + "");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    @Override
    public IndexedTimer addTag(String name, String value) {
        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.put(name,value);
        return this;
    }
}
