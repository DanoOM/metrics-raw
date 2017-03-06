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
    private final Map<MetricKey, Gauge> meters = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledThreadPoolExecutor pools = new ScheduledThreadPoolExecutor(10, new DaemonThreadFactory());
    // registries stored by prefix
    private static final Map<String, MetricRegistry> registries = new ConcurrentHashMap<>();
    private boolean useStartTimeAsEventTime = false;
    private boolean enableMilliIndexing = false;

    public static class Builder {
        private Map<String,String> tags = new HashMap<>();
        private final String prefix;
        private boolean startTimeStrategy = false;

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

        	String tmp  = serviceTeam + "." + application + "." + applicationType + ".";

        	// ensure consistent case here.
        	prefix = tmp.toLowerCase();
            tags.put("host", hostTag.toLowerCase());
            tags.put("datacenter", datacenterTag.toLowerCase());
        }

        // default is false
        public Builder withTimerStrategy(boolean u) {
            startTimeStrategy = u;
            return this;
        }

        public Builder addTag(String tag, String value) {
            tags.put(tag, value);
            return this;
        }

        public MetricRegistry build() {
            MetricRegistry mr = registries.get(prefix);
            if (mr == null) {
                synchronized (registries) {
                    mr = registries.get(prefix);
                    if (mr == null) {
                        if (tags.size() > 0)
                            mr = new MetricRegistry(prefix, startTimeStrategy, tags);
                        else
                            mr = new MetricRegistry(prefix, startTimeStrategy);
                    }
                }
            }
            return mr;
        }
    }

    MetricRegistry(String prefix, boolean startTimeStrategy, Map<String,String> tags) {
    	this.prefix = prefix;
        this.tags = tags;
        registries.put(prefix.substring(prefix.length() - 1) , this);
        useStartTimeAsEventTime = startTimeStrategy;
    }

    MetricRegistry(String prefix, boolean startTimeStrategy) {
    	this.prefix = prefix;
        this.tags = null;
        useStartTimeAsEventTime = startTimeStrategy;
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

    public Meter scheduleMeter(String name, int intervalInSeconds, String...tags) {
        MetricKey key = new MetricKey(name, Util.buildTags(tags));
        Gauge meter = meters.get(key);
        if (meter == null) {
            synchronized (meters) {
                meter = meters.get(key);
                if (meter == null) {
                    meter = new MeterImpl();
                    meters.put(key,meter);
                    pools.scheduleWithFixedDelay(new GaugeRunner(key, meter, this),
                                                 0,
                                                 intervalInSeconds,
                                                 TimeUnit.SECONDS);
                }
            }
        }
        return (Meter)meter;
    }


    public void addEventListener(EventListener listener) {
        if (!listeners.contains(listener)) {
            synchronized (listeners) {
                if (!listeners.contains(listener)) {
                    if (listener instanceof EventIndexingListener){
                        enableMilliIndexing = true;
                    }
                    listeners.add(listener);
                }
            }
        }
    }

    public void removeEventListener(EventListener listener) {
        listener.stop();
        listeners.remove(listener);
    }

    public void removeAllEventListeners() {
        for(EventListener listener : listeners) {
            listener.stop();
        }
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

    private Map<MetricKey,ResetCounter> counts = new ConcurrentHashMap<>();
    void dispatchEvent(EventImpl e) {
        if (enableMilliIndexing) {
            handleIndexing(e);
        }
        listeners.stream().forEach( l -> l.onEvent(e));
    }

    private void handleIndexing(EventImpl e) {
        ResetCounter counter = counts.get(e.getHash());
        if (counter == null) {
            synchronized (counts) {
                counter = counts.get(e.getHash());
                if (counter == null) {
                    counter = new ResetCounter();
                    counts.put(e.getHash(),counter);
                }
            }
        }
        try {
            e.setIndex(counter.incrementAndGet());
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
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


// used to 'sub-index' on milli
class ResetCounter {
    public AtomicInteger counter = new AtomicInteger();
    public long ts;
    public int incrementAndGet() {
        int count = counter.incrementAndGet();
        if (System.currentTimeMillis() - ts  > 1) {
            synchronized (this) {
                ts = System.currentTimeMillis();
                counter.set(0);
            }
        }
        return count;
    }
}