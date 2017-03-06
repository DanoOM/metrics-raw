package org.dshops.metrics;

import java.util.Map;

public interface Event {
    public Map<String,String> getTags();
    public String getName();
    public long getTimestamp();
    default public long getLongValue(){ return 1; }
    default public double getDoubleValue(){ return 0;}
    public MetricKey getHash();

    // 'can be used' by listener if 'sub-indexing is needed'
    // not will not work if 2 listeners are 'changing this..'
    public int getIndex();
    public void setIndex(int index);
}
