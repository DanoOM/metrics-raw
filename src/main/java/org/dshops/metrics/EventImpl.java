package org.dshops.metrics;

import java.util.Collections;
import java.util.Map;

public abstract class EventImpl implements Event {
    private final String name;                // event name
    protected Map<String,String> tags;        // tags associated to event
    protected final long time;                // time of event
    private MetricKey metricKey;

    EventImpl(final String name, final Map<String,String> tags, final long time) {
        this.name = name;
        this.tags = tags;
        this.time = time;
    }

    @Override
    public Map<String,String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    @Override
    public long getTimestamp() {
        return this.time;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" ").append(getName()).append(" ");
        if (tags != null) {
            for (Map.Entry<String,String> e : tags.entrySet()){
                sb.append(e.getKey()).append("=").append(e.getValue());
                sb.append(",");
            }
            sb.setLength(sb.length()-1);
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MetricKey getHash() {
        if (metricKey == null) {
            metricKey = new MetricKey(name, tags);
        }
        return metricKey;
    }

    int index;
    @Override
    public int getIndex() {
        return index;
    }

    void setIndex(int index) {
        this.index = index;
    }

}
