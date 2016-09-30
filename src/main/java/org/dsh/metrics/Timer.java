package org.dsh.metrics;

import java.util.HashMap;
import java.util.Map;

public class Timer extends MetricBase {
	private long startTime;

    Timer(String name, MetricRegistry registry) {
        super(name, registry, null);
    }

    Timer(String name, MetricRegistry registry, Map<String,String> customTags) {
        super(name, registry, customTags);
    }

    /** Returns a new timer, with startTime = now */
    Timer start() {
    	startTime = System.currentTimeMillis();
    	return this;
    }

    /** calculates the time from the starttime, also triggers an event for Listeners */
    public long stop() {
    	long duration = System.currentTimeMillis() - startTime;
    	registry.postEvent(name, startTime, tags, duration);
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
