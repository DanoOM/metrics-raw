package org.dsh.metrics;

import java.util.Collections;
import java.util.Map;

abstract class MetricBase implements Metric {
    protected final String name;
    protected final Map<String,String> tags;
    protected final MetricRegistry registry;

    MetricBase(String name, MetricRegistry registry, Map<String,String> tags) {
    	this.name = name;
    	this.registry = registry;
        this.tags = tags;
    }

    MetricRegistry getMetricRegistry() {
        return registry;
    }

    @Override
    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }
}
