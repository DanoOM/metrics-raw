package org.dshops.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
@SuppressWarnings("rawtypes")
public class MetricRegistry {
	private final String prefix;
    private final Map<String,String> registryTags;
    private final Map<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    
	private final Map<MetricKey, Gauge> gauges = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> meters = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    private ScheduledThreadPoolExecutor pools = null; 
    // registries stored by prefix
    private static final Map<String, List<MetricRegistry>> registries = new ConcurrentHashMap<>();
    private boolean useStartTimeAsEventTime = false;
    private boolean enableMilliIndexing = false;
    private static boolean enableRegistryCache = true;

    public static class Builder {
        private Map<String,String> tags = new HashMap<>();
        private final String prefix;
        private boolean startTimeStrategy = false;

        /** @param namespace - Namespace
         *  @param application  - Application name
         *  @param applicationType - The type of application/library - aka, server, client, test, etc.
         *  @param hostTag - The hostTag these metrics should be associated with
         *  @param datacenterTag - The datacenter tag these metrics should be associated with.
         *  A prefix for each metric will be generated using:
         *
         *     namespace.Application.applicationType
         *
         *  */
        public Builder(String namespace,
                       String application,
                       String applicationType,
                       String hostTag,
                       String datacenterTag) {
        	if (namespace == null || application == null || applicationType == null)
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot be null");
        	if (namespace.contains(".") || application.contains(".") || applicationType.contains("."))
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot contain the character '.'");

        	String tmp  = namespace + "." + application + "." + applicationType + ".";

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
            List<MetricRegistry> regs = registries.get(prefix);            
            if (regs != null) {
                // tag set must match.
                for (MetricRegistry mr : regs) {
                    if (mr.registryTags.equals(tags))
                        return mr;
                }
            }            
            synchronized (registries) {
                if (regs != null) {
                    // tag set must match.
                    for (MetricRegistry mr : regs) {
                        if (mr.registryTags.equals(tags))
                            return mr;
                    }
                }   
                MetricRegistry mr = null; 
                if (tags.size() > 0)
                    mr = new MetricRegistry(prefix, startTimeStrategy, tags);
                else
                    mr = new MetricRegistry(prefix, startTimeStrategy);
                return mr;
            }
        }
    }

    MetricRegistry(String prefix, boolean startTimeStrategy, Map<String,String> tags) {
    	this.prefix = prefix;
        this.registryTags = tags;
        if (enableRegistryCache) {
            List<MetricRegistry> lst = null;
            if (registries.containsKey(prefix)) {
                lst = registries.get(prefix);
            }
            else {
                lst = new LinkedList<>();
                registries.put(prefix, lst);
            }
            lst.add(this);            
        }
        useStartTimeAsEventTime = startTimeStrategy;
    }

    MetricRegistry(String prefix, boolean startTimeStrategy) {
    	this.prefix = prefix;
        this.registryTags = null;
        useStartTimeAsEventTime = startTimeStrategy;
        if (enableRegistryCache) {
            List<MetricRegistry> lst = null;
            if (registries.containsKey(prefix)) {
                lst = registries.get(prefix);
            }
            else {
                lst = new LinkedList<>();
                registries.put(prefix, lst);
            }
            lst.add(this);
        }
    }

    /** By Default registries created with the same signature will be re-used,
     * this can be disabled (typically done for testing).
     * Changing this value after a registry is created as know effect.
     * @param enableRegistryCaching flag to enable/disable registry caching (re-use) 
     * */
    public static void enableRegistryCaching(boolean enableRegistryCaching) {
        enableRegistryCache = enableRegistryCaching;
    }

    public static boolean isRegistryCachingEnabled() {
        return enableRegistryCache;
    }

    /** Returns a previously created registry, with provided prefix, and tags
     *  note: not all tags must be provided, but every tag provided must be match
     *  if no tags as provided, then' first' registry found with matching prefix will be returned.
     * 
     * @param prefix the prefix for the registry
     * @param tags The list of tags for the registry.  Only discriminating tags are needed.
     * @return The matching MetricRegistry in question, or null if not found.
     * */
    public static MetricRegistry getRegistry(String prefix, String... tags) {
        List<MetricRegistry> lst;
        if (!prefix.endsWith(".")) {            
            lst = registries.get(prefix + ".");
        }
        else {
            lst = registries.get(prefix);
        }
        if (tags == null && lst != null && lst.size()>0) {
            return lst.get(0);
        }

        if (lst != null && tags!=null && tags.length > 0) {
            Map<String,String> map = Util.buildTags(tags);            
            for (MetricRegistry mr : lst) {
                boolean match = true;
                for (Map.Entry<String,String> e: map.entrySet()) {
                    String val = mr.getTags().get(e.getKey());
                    if (val == null || !val.equals(e.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return mr;
                }                
            }
        }        
        return null;
    }

    public String getPrefix() {
        return prefix;
    }

    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(registryTags);
    }

    public Timer getTimer(String name) {
        return new RawTimer(name +".timer", this, useStartTimeAsEventTime);
    }

    public Timer getTimer(String name, String...tags) {
        return new RawTimer(name +".timer", this, useStartTimeAsEventTime);
    }

    public Timer timer(String name) {
    	return new RawTimer(name +".timer", this, useStartTimeAsEventTime).start();
    }

    public Timer timer(String name, String...tags) {
    	return timer(name, Util.buildTags(tags));
    }

    public Timer timer(String name, Map<String,String> tags) {
    	return new RawTimer(name+".timer", this, tags, useStartTimeAsEventTime).start();
    }

    public Timer percentileTimer(String name) {
        return new PercentileTimer(name +".timer", this).start();
    }

    public Timer percentileTimer(String name, String...tags) {
        return percentileTimer(name, Util.buildTags(tags));
    }

    public Timer percentileTimer(String name, Map<String,String> tags) {
        return new PercentileTimer(name+".timer", this, tags, useStartTimeAsEventTime).start();
    }

    /** Counters not recommended for real use, but may be
     * useful for testing/early integration. 
     * @param name the name of the counter
     * @return the counter
     * */
    public Counter counter(String name) {
        name = name+".counter";
        MetricKey key = new MetricKey(name);
        Counter c = getCounters().get(key);        
        if (c == null) {
            synchronized (getCounters()) {
                c = getCounters().get(key);
                if (c == null) {
                    Counter tmp = new Counter(name, this);
                    getCounters().put(key,tmp);
                    return tmp;
                }
            }
        }
        return c;
    }

    public Counter counter(String name, String... tags) {
    	return counter(name, Util.buildTags(tags));
    }

    public Counter counter(String name, Map<String,String> tags) {
        name = name+".counter";
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
     *     tag will contain a tag, called alertName, with the alertName passed here.
     *     @param alertName the alername, will manifest in the name tag
     * */
   
    @SuppressWarnings("unchecked")
	public void alert(String alertName) {
        alert(alertName, 1, Collections.EMPTY_MAP);
    }
    @SuppressWarnings("unchecked")
    public void alert(String alertName, String...customTags) {
        alert(alertName, 1, Collections.EMPTY_MAP);
    }

    @SuppressWarnings("unchecked")
    public void alert(String alertName, long value) {
        alert(alertName, value, Collections.EMPTY_MAP);
    }

    public void alert(String alertName, long value, String...customTags) {
        alert(alertName, value, Util.buildTags(customTags));
    }

    public void alert(String alertName, long value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        ctags.put("alertName", alertName);
        dispatchEvent(new LongEvent(prefix + "alerts", ctags, System.currentTimeMillis(), value));
    }

    @SuppressWarnings("unchecked")
    public void alert(String alertName, double value) {
        alert(alertName, value, Collections.EMPTY_MAP);
    }

    public void alert(String alertName, double value, String...customTags) {
        alert(alertName, value, Util.buildTags(customTags));
    }

    public void alert(String alertName, double value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        ctags.put("alertName", alertName);
        dispatchEvent(new DoubleEvent(prefix + "alerts", ctags, System.currentTimeMillis(), value));
    }

    public void event(String name) {
        event(name,1);
    }

    @SuppressWarnings("unchecked")
    public void event(String name, long value) {
        event(name,value,Collections.EMPTY_MAP);
    }

    @SuppressWarnings("unchecked")
    public void event(String name, double value) {
        event(name,value,Collections.EMPTY_MAP);
    }

    public void event(String name, String...customTags) {
        event(name,1,customTags);
    }

    public void event(String name, long value, String...customTags) {
        event(name,value,Util.buildTags(customTags));
    }

    public void event(String name, double value, String...customTags) {
        event(name,value,Util.buildTags(customTags));
    }

    public void event(String name, Map<String,String> customTags) {
        event(name,1,customTags);
    }

    public void event(String name, long value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new LongEvent(prefix + name + ".event", ctags, System.currentTimeMillis(),value));
    }

    public void event(String name, double value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new DoubleEvent(prefix + name + ".event", ctags, System.currentTimeMillis(),value));
    }

    public void eventAtTs(String name, long ts) {
        eventAtTs(name, ts, 1);
    }
    
    @SuppressWarnings("unchecked")
    public void eventAtTs(String name, long ts, long value) {
        eventAtTs(name, ts, value, Collections.EMPTY_MAP);
    }
    @SuppressWarnings("unchecked")
    public void eventAtTs(String name, long ts, double value) {
        eventAtTs(name, ts, value, Collections.EMPTY_MAP);
    }
    public void eventAtTs(String name, long ts, Map<String,String> customTags) {
        eventAtTs(name, ts, 1, customTags);
    }
    
    public void eventAtTs(String name, long ts, double value, String...customTags) {
        eventAtTs(name, ts, value, Util.buildTags(customTags));
    }
    public void eventAtTs(String name, long ts, long value, String...customTags) {
        eventAtTs(name, ts, value, Util.buildTags(customTags));
    }
    public void eventAtTs(String name, long ts, double value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new DoubleEvent(prefix + name + ".event", ctags, ts, value));
    }
    public void eventAtTs(String name, long ts, long value, Map<String,String> customTags) {
        Map<String, String> ctags = mergeTags(customTags);
        dispatchEvent(new DoubleEvent(prefix + name + ".event", ctags, ts, value));
    }

    public void eventBucket(String name) {
        eventBucket(name, 1);
    }

    public void eventBucket(String name, String...customTags) {
        eventBucket(name, 1l, Util.buildTags(customTags));
    }

    public void eventBucket(String name, long value) {
        new EventBucket(name, this).update(value);
    }

    public void eventBucket(String name, double value) {
        new EventBucket(name, this).update(value);
    }

    public void eventBucket(String name, long value, String...customTags) {
        eventBucket(name, value,Util.buildTags(customTags));
    }

    public void eventBucket(String name, double value, String...customTags) {
        eventBucket(name, value, Util.buildTags(customTags));
    }

    public void eventBucket(String name, double value, Map<String,String> customTags) {
        new EventBucket(name, this, customTags).update(value);
    }

    public void eventBucket(String name, long value, Map<String,String> customTags) {
        new EventBucket(name, this, customTags).update(value);
    }


    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge, String...tags) {
    	scheduleGauge(name,intervalInSeconds, gauge, Util.buildTags(tags));
    }

    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge, Map<String,String> tags) {
        name = name + ".gauge";
        MetricKey key = new MetricKey(name, tags);
        if (!gauges.containsKey(key)) {
            synchronized (gauge) {
                if (!gauges.containsKey(key)) {
                    gauges.put(key, gauge);
                    pools.scheduleWithFixedDelay(new GaugeRunner<>(key, gauge, this),
                                                 0,
                                                 intervalInSeconds,
                                                 TimeUnit.SECONDS);
                }
            }
        }
    }

    /** Allows the provided gauge to invoked at millisecond accuracy, but will only report the max result of those calls at the reportInterval 
     * @param name name of gauge 
     * @param collectionIntervalInMillis frequency the values is inspected (frequency function is invoked)
     * @param reportIntervalInSeconds frequency to report the value
     * @param gauge the gauge to invoke
     * @param tags one more more tags
     * 
     * */
    public void scheduleMaxGauge(String name, int collectionIntervalInMillis, int reportIntervalInSeconds, Gauge<? extends Number> gauge, String...tags) {
        scheduleMaxGauge(name,collectionIntervalInMillis, reportIntervalInSeconds, gauge, Util.buildTags(tags));
    }

    public void scheduleMaxGauge(String name, int collectionIntervalInMilis, int reportIntervalInSeconds, Gauge<? extends Number> gauge, Map<String,String> tags) {
        name = name + ".gauge";
        MetricKey key = new MetricKey(name, tags);
        if (!gauges.containsKey(key)) {
            synchronized (gauge) {
                if (!gauges.containsKey(key)) {
                    gauges.put(key, gauge);
                    pools.scheduleWithFixedDelay(new GaugeRunner<>(key, gauge, reportIntervalInSeconds, this),
                                                 0,
                                                 collectionIntervalInMilis,
                                                 TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
	public Meter scheduleMeter(String name, int intervalInSeconds, String...tags) {
        name = name + ".meter";
        MetricKey key = new MetricKey(name, Util.buildTags(tags));
        Gauge meter = meters.get(key);
        if (meter == null) {
            synchronized (meters) {
                meter = meters.get(key);
                if (meter == null) {
                    meter = new MeterImpl();
                    meters.put(key,meter);
                    pools.scheduleWithFixedDelay(new GaugeRunner<>(key, meter, this),
                                                 0,
                                                 intervalInSeconds,
                                                 TimeUnit.SECONDS);                   
                }
            }
        }
        return (Meter)meter;
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

    public void addEventListener(EventListener listener) {
    	if (pools == null) {
    		// ensure thread pool is up
    		synchronized (listeners) {
    			if (pools == null)
    				pools = new ScheduledThreadPoolExecutor(4, new DaemonThreadFactory());				
			}
    	}

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

    /** 
     * @deprecated use removeAllEventListeners(boolean stop)
     * @param listener Eventlistener to remove
     * */
    @Deprecated
    public void removeEventListener(EventListener listener) {
        listener.stop();        
        listeners.remove(listener);
        if (listeners.isEmpty()) {
        	try {
        		pools.shutdown();
        		
        	}
        	catch(Exception e) {
        		// no-op
        	}
        	finally {
        		pools = null;
        	}
        }
    }
    
    /**
     * Will remove the specified listener from this MetricRegistry
     * If stop = true, will also stop the listener if the listener is shared with other
     * MetricRegistries, this will STOP for all metricRegistries!
     * @param listener Eventlistener to remove
     * @param stop set to true to also terminate the listener
     * */
    public void removeEventListener(EventListener listener, boolean stop) {
        
    	if (stop) {
    		listener.stop();
    		gauges.clear();
            meters.clear();
            counters.clear();
    	}
    	
        listeners.remove(listener);
        if (listeners.isEmpty()) {
        	try {
        		gauges.clear();
                meters.clear();
        		pools.shutdown();        		
        	}
        	catch(Exception e) {
        		// no-op
        	}
        	finally {
        		pools = null;
        	}
        }
    }

    /** 
     * @deprecated use removeAllEventListeners(boolean stop)
     * */
    @Deprecated    
    public void removeAllEventListeners() {
    	removeAllEventListeners(true);
    }

    /**
     * Will remove all listeners for this metricRegistory
     * If stop = true, will also stop the listener if the listener is shared with other
     * MetricRegistries, this will STOP for all metricRegistries!
     * @param stop set to true to terminate/stop all event listeners
     * */
    public void removeAllEventListeners(boolean stop) {
    	if (stop) {
	        for(EventListener listener : listeners) {
	            listener.stop();
	        }
    	}
        listeners.clear();
        try
        {
        	gauges.clear();
            meters.clear();
            counters.clear();
        	pools.shutdown();        	
        }
    	catch(Exception e) {
    		// no-op
    	}
        finally {
    		pools = null;
    	}      
    }
    
    public List<EventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

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

    void postEvent(String name, long ts, long value) {
        EventImpl e = new LongEvent(prefix + name, registryTags, ts, value);
        dispatchEvent(e);
    }

    void postEvent(String name, long ts, double value) {
        EventImpl e = new DoubleEvent(prefix + name, registryTags, ts, value);
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
                    counter = new ResetCounter(e.getTimestamp());
                    counts.put(e.getHash(), counter);
                    int index = counter.incrementAndGet() ;
                    e.setIndex(index);
                    return;
                }
            }
        }
        try {
            int index = counter.incrementAndGet() ;
            e.setIndex(index);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    Map<MetricKey, Counter> getCounters() {
        return counters;
    }
}

// used to 'sub-index' on milli
class ResetCounter {
    public AtomicInteger counter = new AtomicInteger();
    public long ts;
    public ResetCounter(Long ts) {
        this.ts = ts;
    }
    public int incrementAndGet() {
        int count = 1;
        if (System.currentTimeMillis() - ts  > 1) {
            synchronized (this) {
                ts = System.currentTimeMillis();
                counter.set(1);
            }
        }
        else {
            count = counter.incrementAndGet();
        }
        return count;
    }
}