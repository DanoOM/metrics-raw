package org.dsh.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class EventImpl implements Event {
    private final String name;                // event name
    protected Map<String,String> tags;        // tags associated to event
    protected final long time;                // time of event
    protected final EventType eventType;

    EventImpl(final String name, final Map<String,String> tags, final EventType type, final long time) {
        this.name = name;
        this.tags = tags;
        this.time = time;
        this.eventType = type;
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
    public EventType getType(){
        return eventType;
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

        public Builder(String name, MetricRegistry registry, EventType eventType) {
            this.registry = registry;
            this.event = new LongEvent(registry.getPrefix() + name, new HashMap<String,String>(), eventType, System.currentTimeMillis(),1l);
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
