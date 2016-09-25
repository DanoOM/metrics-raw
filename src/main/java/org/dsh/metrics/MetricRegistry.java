package org.dsh.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dsh.metrics.EventImpl.Builder;

public class MetricRegistry {
    private final Map<String,String> tags;
    private final Map<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    private final Map<MetricKey, Gauge> gauges = new ConcurrentHashMap<>();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    private final ScheduledThreadPoolExecutor pools = new ScheduledThreadPoolExecutor(10);

    public MetricRegistry(Map<String,String> tags) {
        if (tags != null) {
            this.tags = new ConcurrentHashMap<>();
            this.tags.putAll(tags);
        }
        else {
            this.tags = new ConcurrentHashMap<>();
        }
    }

    public MetricRegistry() {
        tags = new ConcurrentHashMap<>();
    }

    /** All Metrics generated will include all provided tags */
    public void addTag(String tag, String value) {
        this.tags.put(tag, value);
    }

    public Timer timer(String name) {
    	return new Timer(name, this);
    }

    public Timer.Builder timerWithTags(String name) {
    	return new Timer.Builder(name, this);
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

    /** Counters not recommended for real use, but may be
     * useful for testing/early integration.
     * Counters with tags are extra expensive. */
    public Counter.Builder counterWithTags(String name) {
        Counter.Builder cb = new Counter.Builder(name, this);
        return cb;
    }

    public void event(String name) {
        dispatchEvent(new LongEvent(name, tags, System.currentTimeMillis(),1));
    }

    public Builder eventWithTags(String name) {
        return new Builder(name, this);
    }

    public void scheduleGauge(String name, int intervalInSeconds, Gauge<? extends Number> gauge) {
        MetricKey key = new MetricKey(name, gauge.getTags());

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

    void postEvent(String name, long ts, Map<String,String> customTags, Number number) {
        EventImpl e;
        Map<String,String> ctags = new HashMap<String,String>();
        ctags.putAll(tags);
        if (customTags != null) {
        	ctags.putAll(customTags);
        }

        if (number instanceof Double) {
            e = new DoubleEvent(name, ctags, System.currentTimeMillis(), number.doubleValue());
        }
        else {
            e = new LongEvent(name, ctags, System.currentTimeMillis(), number.longValue());
        }
        dispatchEvent(e);
    }

    void postEvent(String name, long ts, long value) {
        EventImpl e = new LongEvent(name, tags, System.currentTimeMillis(), value);
        dispatchEvent(e);

    }

    void postEvent(String name, long ts, double value) {
        EventImpl e = new DoubleEvent(name, tags, System.currentTimeMillis(), value);
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

