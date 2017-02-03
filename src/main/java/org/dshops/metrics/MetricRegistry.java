package org.dshops.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MetricRegistry {
	private final String prefix;
    private final Map<String,String> tags;
    private final Map<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> gauges = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledThreadPoolExecutor pools = new ScheduledThreadPoolExecutor(10);
    // registries stored by prefix
    private static final Map<String, MetricRegistry> registries = new ConcurrentHashMap<>();

    public static class Builder {
        private Map<String,String> tags = new HashMap<>();
        private final String prefix;

        /** @param serviceTeam - application Domain (service team)
         *  @param application  - application name
         *  A prefix for each metric will be generated, serviceTeam.Aapplication.
         *
         *  */
        public Builder(String serviceTeam, String application, String applicationType) {
        	if (serviceTeam == null || application == null || applicationType == null)
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot be null");
        	if (serviceTeam.contains(".") || application.contains(".") || applicationType.contains(".")){
        		throw new IllegalArgumentException("serviceTeam, application, and/or applicationType cannot contain the character '.'");
        	}
        	prefix = serviceTeam + "." + application + "." + applicationType + ".";
        }

        public Builder withHost(String host) {
            tags.put("host", host);
            return this;
        }

        public Builder withDatacenter(String datacenter){
            tags.put("datacenter", datacenter);
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
    	return new Timer(name, this).start();
    }

    public Timer.Builder timerWithTags(String name) {
    	return new Timer.Builder(name, this);
    }

    public Timer timer(String name, String...tags) {
    	return timer(name, Util.buildTags(tags));
    }

    public Timer timer(String name, Map<String,String> tags) {
    	return new Timer(name, this, tags).start();
    }

    // construct a timer, but aggregate time values into buckets of size bucketTimeSeconds
    public Timer timer(String name, int bucketTimeSeconds, String...tags) {
        return timer(name, Util.buildTags(tags));
    }

    /** Counstruct a timer such that timers created in the same name / primaryTag within
     * the same milli,, will be indexed by an 'index' tag, (allowing sub-millis data resolution) */
    public Timer indexedTimer(String name, String primaryTag, String...tags) {
        return timer(name, Util.buildTags(tags));
    }

    /** Counstruct a timer such that timers created in the same name / primaryTag within
     * the same milli,, will be indexed by an 'index' tag, (allowing sub-millis data resolution) */
    public Timer indexedTimer(String name, String primaryTag, Map<String,String> tags) {
        return new Timer(name, this, tags).start();
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
        dispatchEvent(new LongEvent(prefix + name, tags, EventType.Event, System.currentTimeMillis(),value));
    }

    public void event(String name, double value) {
        dispatchEvent(new DoubleEvent(prefix + name, tags, EventType.Event, System.currentTimeMillis(),value));
    }

    public void event(String name, String...customTags) {
        event(name,1,customTags);
    }

    public void event(String name, long value, String...customTags) {
        dispatchEvent(new LongEvent(prefix + name, Util.buildTags(customTags), EventType.Event, System.currentTimeMillis(),value));
    }
    public void event(String name, double value, String...customTags) {
        dispatchEvent(new DoubleEvent(prefix + name, Util.buildTags(customTags), EventType.Event, System.currentTimeMillis(),value));
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
        dispatchEvent(new LongEvent(prefix + name, tags, EventType.Event,  System.currentTimeMillis(),value));
    }

    public void event(String name, double value, Map<String,String> customTags) {
        Map<String,String> ctags = new HashMap<>();

        if (tags != null) {
            ctags.putAll(tags);
        }
        if (customTags != null) {
            ctags.putAll(customTags);
        }
        dispatchEvent(new DoubleEvent(prefix + name, tags, EventType.Event,  System.currentTimeMillis(),value));
    }

    public EventImpl.Builder eventWithTags(String name) {
        return new EventImpl.Builder(name, this, EventType.Event);
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

    Map<MetricKey, ConcurrentSkipListMap<Long, Collection<Number>>> data = new ConcurrentSkipListMap<>(); // ts/value

    void addToBucket(String name, Map<String,String> customTags, long ts, Number number, EventType type) {
        MetricKey key = new MetricKey(name, customTags);
        ConcurrentSkipListMap<Long, Collection<Number>> buckets = data.get(key);
        if (buckets == null) {
            synchronized (data) {
                buckets = data.get(key);
                if (buckets == null) {
                    buckets = new ConcurrentSkipListMap<>();
                    data.put(key, buckets);
                }
            }
        }
        addMetric(ts, number, buckets);
    }

    private void addMetric(long endTime, Number duration, ConcurrentSkipListMap<Long,Collection<Number>> buckets) {
        Collection<Number> values = buckets.get(endTime);
        if (values == null) {
            values = new ConcurrentLinkedQueue<>();
            buckets.put(endTime, values);
        }
        values.add(duration);
    }

    void postEvent(String name, long ts, Map<String,String> customTags, Number number, EventType type) {
        EventImpl e;
        Map<String,String> ctags = new HashMap<>();

        if (tags != null) {
            ctags.putAll(tags);
        }
        if (customTags != null) {
        	ctags.putAll(customTags);
        }

        if (number instanceof Double) {
            e = new DoubleEvent(prefix + name, ctags, type, ts, number.doubleValue());
        }
        else {
            e = new LongEvent(prefix + name, ctags, type, ts, number.longValue());
        }
        dispatchEvent(e);
    }

    void postEvent(String name, long ts, long value, EventType type) {
        EventImpl e = new LongEvent(prefix + name, tags, type, ts, value);
        dispatchEvent(e);

    }

    void postEvent(String name, long ts, double value, EventType type) {
        EventImpl e = new DoubleEvent(prefix + name, tags, type, ts, value);
        dispatchEvent(e);
    }

    void dispatchEvent(Event e) {
        listeners.stream().forEach( l -> l.onEvent(e));
    }

    Map<MetricKey, Counter> getCounters() {
        return counters;
    }
}

// used when counters/gauges are created
// consideration: if remove counters from the system
// and 'allow' users to create duplicate gauges, this and their internal
// maps can be removed.
class MetricKey {
  	final String name;
    final Map<String,String> tags;

    public MetricKey(String name) {
        this.name = name;
        this.tags = null;
    }

    public MetricKey(String name, Map<String,String> tags) {
        this.name = name;
        this.tags = tags;
    }

    @Override
  	public int hashCode() {
  		final int prime = 31;
  		int result = 1;
  		result = prime * result + ((name == null) ? 0 : name.hashCode());
  		result = prime * result + ((tags == null) ? 0 : tags.hashCode());
  		return result;
  	}

  	@Override
  	public boolean equals(Object obj) {
  		if (this == obj)
  			return true;
  		if (obj == null)
  			return false;
  		if (getClass() != obj.getClass())
  			return false;
  		MetricKey other = (MetricKey) obj;
  		if (name == null) {
  			if (other.name != null)
  				return false;
  		} else if (!name.equals(other.name))
  			return false;
  		if (tags == null) {
  			if (other.tags != null)
  				return false;
  		} else if (!tags.equals(other.tags))
  			return false;
  		return true;
  	}
}

