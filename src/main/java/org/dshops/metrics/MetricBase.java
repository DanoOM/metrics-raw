package org.dshops.metrics;

import java.util.Collections;
import java.util.Map;

abstract class MetricBase implements Metric {
    protected final String name;
    protected Map<String,String> tags;
    protected final MetricRegistry registry;
    protected final String primaryTag;
    MetricBase(String name, final String primaryTag, MetricRegistry registry, Map<String,String> tags) {
    	this.name = name;
    	this.registry = registry;
        this.tags = tags;
        this.primaryTag = primaryTag;
    }

    MetricRegistry getMetricRegistry() {
        return registry;
    }

    @Override
    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }
}
