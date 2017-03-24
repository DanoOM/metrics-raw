package org.dshops.metrics;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BucketMetricRegistry extends MetricRegistry {
	private final String prefix;
    private final Map<String,String> registryTags;
    private final Map<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> gauges = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> meters = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledThreadPoolExecutor pools = new ScheduledThreadPoolExecutor(10, new DaemonThreadFactory());
    // registries stored by prefix
    private static final Map<String, BucketMetricRegistry> registries = new ConcurrentHashMap<>();
    private boolean useStartTimeAsEventTime = false;
    private boolean enableMilliIndexing = false;
    private static boolean enableRegistryCache = true;
    private long bucketWindow;
    private BucketProcessor bucketProcessor = new BucketProcessor();

    public static class Builder {
        private Map<String,String> tags = new HashMap<>();
        private final String prefix;
        private boolean startTimeStrategy = false;
        private long bucketWindow;

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

        public Builder withBucketSize(long bucketSizeMillis) {
            bucketWindow = bucketSizeMillis;
            return this;
        }

        public Builder addTag(String tag, String value) {
            tags.put(tag, value);
            return this;
        }

        public BucketMetricRegistry build() {
            BucketMetricRegistry mr = registries.get(prefix);
            if (mr == null) {
                synchronized (registries) {
                    mr = registries.get(prefix);

                    if (bucketWindow < 1000) {
                        // minimum bucketWindow is 1 second.
                        bucketWindow = 1000;
                    }

                    if (mr == null) {
                        if (tags.size() > 0)
                            mr = new BucketMetricRegistry(prefix, startTimeStrategy, tags, bucketWindow);
                        else
                            mr = new BucketMetricRegistry(prefix, startTimeStrategy, bucketWindow);
                    }
                }
            }
            return mr;
        }
    }

    BucketMetricRegistry(String prefix, boolean startTimeStrategy, Map<String,String> tags, long bucketWindow) {
        super(prefix,startTimeStrategy, tags);
    	this.prefix = prefix;
        this.registryTags = tags;
        if (enableRegistryCache) {
            registries.put(prefix.substring(prefix.length() - 1) , this);
        }
        useStartTimeAsEventTime = startTimeStrategy;
        this.bucketWindow = bucketWindow;

        Thread t = new Thread(bucketProcessor);
        t.setDaemon(true);
        t.start();
    }

    BucketMetricRegistry(String prefix, boolean startTimeStrategy, long bucketWindow) {
        super(prefix, startTimeStrategy);
    	this.prefix = prefix;
        this.registryTags = null;
        useStartTimeAsEventTime = startTimeStrategy;
        if (enableRegistryCache) {
            registries.put(prefix.substring(prefix.length() - 1) , this);
        }
        this.bucketWindow = bucketWindow;

        Thread t = new Thread(bucketProcessor);
        t.setDaemon(true);
        t.start();
    }

    /** By Default registries created with the same signature will be re-used,
     * this can be disabled (typically done for testing).
     * Changing this value after a registry is created as know effect.
     * */
    public static void enableRegistryCaching(boolean enableRegistryCaching) {
        enableRegistryCache = enableRegistryCaching;
    }

    public static boolean isRegistryCachingEnabled() {
        return enableRegistryCache;
    }

    /** Returns a previously created registry, where prefix = serviceTeam.application.appType
     * (note: '.' on end)
     * */
    public static BucketMetricRegistry getRegistry(String prefix) {
        return registries.get(prefix);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(registryTags);
    }

    @Override
    public Timer timer(String name) {
    	return new Timer(name, this, useStartTimeAsEventTime).start();
    }

    @Override
    public Timer timer(String name, String...tags) {
    	return timer(name, Util.buildTags(tags));
    }

    @Override
    public Timer timer(String name, Map<String,String> tags) {
    	return new Timer(name, this, tags, useStartTimeAsEventTime).start();
    }

    // construct a timer, but aggregate time values into buckets of size bucketTimeSeconds
    @Override
    public Timer timer(String name, int bucketTimeSeconds, String...tags) {
        return timer(name, Util.buildTags(tags));
    }

    /** Counters not recommended for real use, but may be
     * useful for testing/early integration. */
    @Override
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

    @Override
    public Counter counter(String name, String... tags) {
    	return counter(name, Util.buildTags(tags));
    }

    @Override
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

    /** Generates an alert, where the metricName is:
     *     ServiceTeam.app.type.alerts
     *     tag will contain a tag, called alertName, with the alertName passed here.s
     * */
    @Override
    public void alert(String alertName) {
        alert(alertName, 1, Collections.EMPTY_MAP);
    }
    @Override
    public void alert(String alertName, String...customTags) {
        alert(alertName, 1, Collections.EMPTY_MAP);
    }

    @Override
    public void alert(String alertName, long value) {
        alert(alertName, value, Collections.EMPTY_MAP);
    }

    @Override
    public void alert(String alertName, long value, String...customTags) {
        alert(alertName, value, Util.buildTags(customTags));
    }

    @Override
    public void alert(String alertName, long value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        ctags.put("alertName", alertName);
        dispatchEvent(new LongEvent(prefix + "alerts", ctags, System.currentTimeMillis(), value), "ALERT");
    }

    @Override
    public void alert(String alertName, double value) {
        alert(alertName, value, Collections.EMPTY_MAP);
    }

    @Override
    public void alert(String alertName, double value, String...customTags) {
        alert(alertName, value, Util.buildTags(customTags));
    }

    @Override
    public void alert(String alertName, double value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        ctags.put("alertName", alertName);
        dispatchEvent(new DoubleEvent(prefix + "alerts", ctags, System.currentTimeMillis(), value), "ALERT");
    }

    /** Returns a map with the customTags + registryTags merged (customTags collisions always win).*/
    private Map<String, String> mergeTags(Map<String, String> customTags) {
        Map<String,String> ctags = new HashMap<>();
        if (registryTags!=null) {
            ctags.putAll(registryTags);
        }
        if (customTags !=null){
            ctags.putAll(customTags);
        }
        return ctags;
    }

    @Override
    public void event(String name) {
        event(name,1);
    }

    @Override
    public void event(String name, long value) {
        event(name,value,Collections.EMPTY_MAP);
    }

    @Override
    public void event(String name, double value) {
        event(name,value,Collections.EMPTY_MAP);
    }

    @Override
    public void event(String name, String...customTags) {
        event(name,1,customTags);
    }

    @Override
    public void event(String name, long value, String...customTags) {
        event(name,value,Util.buildTags(customTags));
    }
    @Override
    public void event(String name, double value, String...customTags) {
        event(name,value,Util.buildTags(customTags));
    }

    @Override
    public void event(String name, Map<String,String> customTags) {
        event(name,1,customTags);
    }

    @Override
    public void event(String name, long value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new LongEvent(prefix + name, ctags, System.currentTimeMillis(),value), "EVENT");
    }

    @Override
    public void event(String name, double value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new DoubleEvent(prefix + name, ctags, System.currentTimeMillis(),value), "EVENT");
    }

    @Override
    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge, String...tags) {
    	scheduleGauge(name,intervalInSeconds, gauge, Util.buildTags(tags));
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public void removeEventListener(EventListener listener) {
        listener.stop();
        listeners.remove(listener);
    }

    @Override
    public void removeAllEventListeners() {
        for(EventListener listener : listeners) {
            listener.stop();
        }
        listeners.clear();
    }

    @Override
    public List<EventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    @Override
    void postEvent(String name, long ts, Map<String,String> customTags, Number number) {
        EventImpl e;
        Map<String, String> ctags = mergeTags(customTags);

        if (number instanceof Double) {
            e = new DoubleEvent(prefix + name, ctags, ts, number.doubleValue());
        }
        else {
            e = new LongEvent(prefix + name, ctags, ts, number.longValue());
        }
        dispatchEvent(e);
    }


    void postEvent(String name, long ts, Map<String,String> customTags, Number number, String metricType) {
        EventImpl e;
        Map<String, String> ctags = mergeTags(customTags);

        if (number instanceof Double) {
            e = new DoubleEvent(prefix + name, ctags, ts, number.doubleValue());
        }
        else {
            e = new LongEvent(prefix + name, ctags, ts, number.longValue());
        }
        dispatchEvent(e, metricType);
    }

    @Override
    void postEvent(String name, long ts, long value) {
        EventImpl e = new LongEvent(prefix + name, registryTags, ts, value);
        dispatchEvent(e);
    }

    @Override
    void postEvent(String name, long ts, double value) {
        EventImpl e = new DoubleEvent(prefix + name, registryTags, ts, value);
        dispatchEvent(e);
    }

    private Map<MetricKey,Bucket> buckets = new ConcurrentHashMap<>();


    void dispatchEvent(EventImpl e, String metricType) {
        handleBucket(e, metricType);
    }

    void notifyListeners(EventImpl e) {
        listeners.stream().forEach( l -> l.onEvent(e));
    }

    long getBucketWindow(){
        return bucketWindow;
    }

    private void handleBucket(EventImpl e, String metricType) {
        Bucket bucket = buckets.get(e.getHash());
        if (bucket == null) {
            synchronized (buckets) {
                bucket = buckets.get(e.getHash());
                if (bucket == null) {
                    bucket = new Bucket(this, metricType);
                    bucketProcessor.addBucket(bucket);
                    buckets.put(e.getHash(),bucket);
                }
            }
        }
        bucket.add(e);
    }

    @Override
    Map<MetricKey, Counter> getCounters() {
        return counters;
    }
}


class Bucket {
    private final String metricType;
    private List<EventImpl> currentEventList = new LinkedList<>();
    private final AtomicLong currentBucket = new AtomicLong(System.currentTimeMillis());
    private final BucketMetricRegistry registry;

    public Bucket(BucketMetricRegistry registry, String metricType) {
        this.registry = registry;
        this.metricType = metricType;
    }

    public void add(EventImpl e) {
        flushIfNeeded();
        currentEventList.add(e);
    }

    void flushIfNeeded() {
        long ts = currentBucket.get();
        long newTime = System.currentTimeMillis();
        List<EventImpl> flushEvents = currentEventList;
        long delta = newTime - ts;
        if (delta >= registry.getBucketWindow()) {
            // move to new bucket
            synchronized (currentBucket) {
                if (currentBucket.get() == ts) {
                    flushEvents = currentEventList;
                    currentEventList = new LinkedList<>();
                    currentBucket.set(newTime);
                }
            }
            if(flushEvents != null) {
                flush(flushEvents, ts);
            }
        }
    }

    class LongEventCompator implements Comparator<Event>{
        @Override
        public int compare(Event o1, Event o2) {
            return (int)(o1.getLongValue() - o2.getLongValue());
        }
    }

    class DoubleEventCompator implements Comparator<Event> {
        @Override
        public int compare(Event o1, Event o2) {
            return (int)(o1.getDoubleValue() - o2.getDoubleValue());
        }
    }

    private void flush(List<EventImpl> events, long timestamp) {
        if (events.isEmpty()) return;
        LongEvent[] sorted = null;
        // @todo sort really only needed if we are a timer.
        if (events.get(0) != null && events.get(0) instanceof LongEvent) {
            sorted = events.stream()
                    .sorted((e1,e2) -> new LongEventCompator().compare(e1, e2))
                    .toArray(size -> new LongEvent[size]);
        }
        String name = sorted[0].getName();
        Map<String,String> tags = sorted[0].getTags();

        long max = sorted[sorted.length - 1].getLongValue();
        long min = sorted[0].getLongValue();
        long count = sorted.length;
        if (metricType.equals("TIMER")) {
            long tp90 = getNines(sorted, 90);
            long tp99 = getNines(sorted,99);
            registry.notifyListeners(new LongEvent(name + ".tp90", tags, timestamp, tp90));
            registry.notifyListeners(new LongEvent(name + ".tp99", tags, timestamp, tp99));
        }

        registry.notifyListeners(new LongEvent(name + ".count", tags, timestamp, count));
        registry.notifyListeners(new LongEvent(name + ".min", tags, timestamp, min));
        registry.notifyListeners(new LongEvent(name + ".max", tags, timestamp, max));
        events.clear();
    }

    public long getNines(LongEvent[] events, int percent) {
        try {
            float percentF = 0;
            if (percent <=99)
                percentF = (float)percent/100;
            else if (percent<=999) {
                percentF = (float)percent/1000;
            }
            else if (percent<=9999) {
                percentF = (float)percent/10000;
            }
            else if (percent<=99999) {
                percentF = (float)percent/100000;
            }
            int index = (int)(percentF * events.length - 1);
            return events[index].getLongValue();
        }
        catch(Exception e) {
            return -1; // not enough samples
        }
    }
}


class BucketProcessor implements Runnable {
    public List<Bucket> buckets = new CopyOnWriteArrayList<>();

    public BucketProcessor() {

    }

    public void addBucket(Bucket bucket) {
        buckets.add(bucket);
    }
    @Override
    public void run() {
        for(;;){
            for (Bucket b: buckets){
                b.flushIfNeeded();
            }
        }
    }
}

