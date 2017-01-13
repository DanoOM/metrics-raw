package org.dshops;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class Counter extends MetricBase {
    private final LongAdder adder = new LongAdder();

    public Counter(String name, MetricRegistry registry) {
        super(name, registry, null);
    }

    public Counter(String name, MetricRegistry registry, Map<String,String> tags) {
        super(name, registry, tags);
    }

    public void increment() {
        adder.increment();
        registry.postEvent(name, System.currentTimeMillis(), adder.longValue(), EventType.Counter); // This 'may not be exact'
    }

    public void decrement() {
        adder.decrement();
        registry.postEvent(name, System.currentTimeMillis(), adder.longValue(), EventType.Counter); // This 'may not be exact'
    }

    public void add(long x){
        adder.add(x);
        registry.postEvent(name, System.currentTimeMillis(), adder.longValue(), EventType.Counter); // This 'may not be exact'
    }

    public static class Builder {
        private Map<String,String> tags = new HashMap<>(); // our actual key will be a String representing this map
        private final MetricRegistry registry;
        private final String name;

        public Builder(String name, MetricRegistry registry) {
            this.registry = registry;
            this.name = name;
        }

        public Builder addTag(String tag, String value) {
            tags.put(tag, value);
            return this;
        }

        public Counter build() {
            // check if counter already exists
        	MetricKey k = new MetricKey(name, tags);
            Counter counter = registry.getCounters().get(k);

            if (counter == null) {
                synchronized (registry) {
                    counter = registry.getCounters().get(k);
                    if (counter == null) {
                        counter = new Counter(name, registry, tags);
                    }
                }
            }
            return counter;
        }
    }
}