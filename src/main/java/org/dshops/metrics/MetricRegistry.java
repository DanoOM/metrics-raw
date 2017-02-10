package org.dshops.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricRegistry {
	private final String prefix;
    private final Map<String,String> tags;
    private final Map<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> gauges = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledThreadPoolExecutor pools = new ScheduledThreadPoolExecutor(10, new DaemonThreadFactory());
    // registries stored by prefix
    private static final Map<String, MetricRegistry> registries = new ConcurrentHashMap<>();
    private boolean useStartTimeAsEventTime = false;

    public static class Builder {
        private Map<String,String> tags = new HashMap<>();
        private final String prefix;

        /** @param serviceTeam - application Domain (service team)
         *  @param application  - application name
         *  @param applicationType - The type of application/library - aka, server, client, test, etc.
         *  @param hostTag - The hostTag these metrics should be associated with
         *  @param datacenterTag = The datacenter tag these metrics should be associated with.
         *  A prefix for each metric will be generated, serviceTeam.Aapplication.
         *
         *  */
        public Builder(String serviceTeam,
                       String application,
                       String applicationType,
                       String hostTag,
                       String datacenterTag) {
        	if (serviceTeam == null || application == null || applicationType == null)
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot be null");
        	if (serviceTeam.contains(".") || application.contains(".") || applicationType.contains("."))
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot contain the character '.'");

        	prefix = serviceTeam + "." + application + "." + applicationType + ".";
            tags.put("host", hostTag);
            tags.put("datacenter", datacenterTag);
        }

        // default is false
        public Builder withTimerStrategy(boolean useStartTimeAsEventTime) {
            useStartTimeAsEventTime = useStartTimeAsEventTime;
            return this;
        }

        public Builder addTag(String tag, String value) {
            tags.put(tag, value);
            return this;
        }

        public MetricRegistry build() {
            if (tags.size() > 0) {
                return new MetricRegistry(prefix, tags);
            }
            return new MetricRegistry(prefix);
        }
    }

    MetricRegistry(String prefix, Map<String,String> tags) {
    	this.prefix = prefix;
        this.tags = tags;
        registries.put(prefix, this);
    }

    MetricRegistry(String prefix) {
    	this.prefix = prefix;
        this.tags = null;
        registries.put(prefix, this);
    }

    public String getPrefix() {
    	return prefix;
    }

    /** Returns a previously created registry, where prefix = serviceTeam.application.appType
     * (note: '.' on end)
     * */
    public static MetricRegistry getRegistry(String prefix) {
        return registries.get(prefix);
    }

    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public Timer timer(String name) {
    	return new Timer(name, this, useStartTimeAsEventTime).start();
    }

    public Timer.Builder timerWithTags(String name) {
    	return new Timer.Builder(name, this,useStartTimeAsEventTime);
    }

    public Timer timer(String name, String...tags) {
    	return timer(name, Util.buildTags(tags));
    }

    public Timer timer(String name, Map<String,String> tags) {
    	return new Timer(name, this, tags, useStartTimeAsEventTime).start();
    }

    // construct a timer, but aggregate time values into buckets of size bucketTimeSeconds
    public Timer timer(String name, int bucketTimeSeconds, String...tags) {
        return timer(name, Util.buildTags(tags));
    }

    /** Counters not recommended for real use, but may be
     * useful for testing/early integration. */
    public Counter counter(String name) {
        Counter c = getCounters().get(new MetricKey(name));
        if (c == null){
            synchronized (getCounters()) {
                c = getCounters().get(name);
                if (c == null) {
                    Counter tmp = new Counter(name, this);
                    getCounters().put(new MetricKey(name),tmp);
                    return tmp;
                }
            }
        }
        return c;
    }

    public Counter counter(String name, String... tags) {
    	return counter(name, Util.buildTags(tags));
    }

    public Counter counter(String name, Map<String,String> tags){
    	MetricKey key = new MetricKey(name,tags);
    	Counter c = getCounters().get(key);
        if (c == null){
            synchronized (getCounters()) {
                c = getCounters().get(key);
                if (c == null) {
                    Counter tmp = new Counter(name, this, tags);
                    getCounters().put(new MetricKey(name, tags),tmp);
                    return tmp;
                }
            }
        }
        return c;
    }

    /** Counters not recommended for real use, but may be
     * useful for testing/early integration.
     * Counters with tags are extra expensive. */
    public Counter.Builder counterWithTags(String name) {
        Counter.Builder cb = new Counter.Builder(name, this);
        return cb;
    }

    public void event(String name) {
        event(name,1);
    }

    public void event(String name, long value) {
        dispatchEvent(new LongEvent(prefix + name, tags,  System.currentTimeMillis(),value));
    }

    public void event(String name, double value) {
        dispatchEvent(new DoubleEvent(prefix + name, tags,  System.currentTimeMillis(),value));
    }

    public void event(String name, String...customTags) {
        event(name,1,customTags);
    }

    public void event(String name, long value, String...customTags) {
        dispatchEvent(new LongEvent(prefix + name, Util.buildTags(customTags),  System.currentTimeMillis(),value));
    }
    public void event(String name, double value, String...customTags) {
        dispatchEvent(new DoubleEvent(prefix + name, Util.buildTags(customTags),  System.currentTimeMillis(),value));
    }

    public void event(String name, Map<String,String> customTags) {
        event(name,1,customTags);
    }

    public void event(String name, long value, Map<String,String> customTags) {
        Map<String,String> ctags = new HashMap<>();

        if (tags != null) {
            ctags.putAll(tags);
        }
        if (customTags != null) {
            ctags.putAll(customTags);
        }
        dispatchEvent(new LongEvent(prefix + name, tags, System.currentTimeMillis(),value));
    }

    public void event(String name, double value, Map<String,String> customTags) {
        Map<String,String> ctags = new HashMap<>();

        if (tags != null) {
            ctags.putAll(tags);
        }
        if (customTags != null) {
            ctags.putAll(customTags);
        }
        dispatchEvent(new DoubleEvent(prefix + name, tags, System.currentTimeMillis(),value));
    }

    public EventImpl.Builder eventWithTags(String name) {
        return new EventImpl.Builder(name, this);
    }

    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge, String...tags) {
    	scheduleGauge(name,intervalInSeconds, gauge, Util.buildTags(tags));
    }

    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge, Map<String,String> tags) {
        MetricKey key = new MetricKey(name, tags);
        if (!gauges.containsKey(key)) {
            synchronized (gauge) {
                if (!gauges.containsKey(key)) {
                    gauges.put(key, gauge);
                    pools.scheduleWithFixedDelay(new GaugeRunner(key, gauge, this),
                                                 0,
                                                 intervalInSeconds,
                                                 TimeUnit.SECONDS);
                }
            }
        }
    }


    public void addEventListener(EventListener listener) {
        if (!listeners.contains(listener)) {
            synchronized (listeners) {
                if (!listeners.contains(listener)) {
                    listeners.add(listener);
                }
            }
        }
    }

    public void removeEventListener(EventListener listener) {
        listeners.remove(listener);
    }

    public void removeAllEventListeners() {
        listeners.clear();
    }

    public List<EventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    void postEvent(String name, long ts, Map<String,String> customTags, Number number) {
        EventImpl e;
        Map<String,String> ctags = new HashMap<>();

        if (tags != null) {
            ctags.putAll(tags);
        }
        if (customTags != null) {
        	ctags.putAll(customTags);
        }

        if (number instanceof Double) {
            e = new DoubleEvent(prefix + name, ctags, ts, number.doubleValue());
        }
        else {
            e = new LongEvent(prefix + name, ctags, ts, number.longValue());
        }
        dispatchEvent(e);
    }

    void postEvent(String name, long ts, long value) {
        EventImpl e = new LongEvent(prefix + name, tags, ts, value);
        dispatchEvent(e);
    }

    void postEvent(String name, long ts, double value) {
        EventImpl e = new DoubleEvent(prefix + name, tags, ts, value);
        dispatchEvent(e);
    }

    void dispatchEvent(Event e) {
        listeners.stream().forEach( l -> l.onEvent(e));
    }

    Map<MetricKey, Counter> getCounters() {
        return counters;
    }
}



class DaemonThreadFactory implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger();
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName("metric-raw-" + (counter.incrementAndGet()));
        t.setDaemon(true);
        return t;
    }
}

