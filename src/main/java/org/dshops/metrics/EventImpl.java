package org.dshops.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class EventImpl implements Event {
    private final String name;                // event name
    protected Map<String,String> tags;        // tags associated to event
    protected final long time;                // time of event

    private String primaryTag;

    EventImpl(final String name, final String primaryTag, final Map<String,String> tags,  final long time) {
        this.name = name;
        this.tags = tags;
        this.time = time;
        this.primaryTag = primaryTag;
    }

    @Override
    public String getPrimaryTag() {
        return this.primaryTag;
    }

    @Override
    public void addTag(String tag, String value){
        if (tags == null){
            tags = new HashMap<>();
        }
        tags.put(tag, value);
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

    public static class Builder {
        private MetricRegistry registry;
        EventImpl event;

        public Builder(String name, MetricRegistry registry) {
            this.registry = registry;
            this.event = new LongEvent(registry.getPrefix() + name, new HashMap<String,String>(), System.currentTimeMillis(),1l);
        }

        public Builder addTag(String tag, String value) {
            this.event.tags.put(tag, value);
            return this;
        }

        public void build() {
            this.registry.dispatchEvent(this.event);
        }
    }

}
