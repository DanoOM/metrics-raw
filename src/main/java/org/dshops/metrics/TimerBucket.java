package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;

// experimental (not to be used atm).
public class TimerBucket extends Timer {

    TimerBucket(String name, MetricRegistry registry, int bucketSize) {
        super(name, registry, null);
    }

    TimerBucket(String name, MetricRegistry registry, Map<String,String> customTags, int bucketSize) {
        super(name, registry, customTags);
    }


    @Override
    public long stop() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        registry.addToBucket(name, tags, endTime, duration, EventType.Timer);
        return duration;
    }

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    @Override
    public long stop(String... tags) {
        return stop(Util.buildTags(tags));
    }

    /** @todo This should error out, or 'not' update the duration on an already stopped timer. */
    @Override
    public long stop(Map<String,String> customTags) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.putAll(customTags);
        registry.addToBucket(name, tags, endTime, duration, EventType.Timer);
        return duration;
    }

}
