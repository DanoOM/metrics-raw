package org.dshops.metrics;

import java.util.Map;

// A Metrickey represents a unique hash for metric+tagset
// obviously if the tagset changes afterwards it will not reflected.
//consideration: if remove counters from the system
//and 'allow' users to create duplicate gauges, this and their internal
//maps can be removed.
public class MetricKey {
 private final String name;
 private final Map<String,String> tags;

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
     result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
     result = prime * result + ((getTags() == null) ? 0 : getTags().hashCode());
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
     if (getName() == null) {
         if (other.getName() != null)
             return false;
     } else if (!getName().equals(other.getName()))
         return false;
     if (getTags() == null) {
         if (other.getTags() != null)
             return false;
     } else if (!getTags().equals(other.getTags()))
         return false;
     return true;
 }

    public Map<String,String> getTags() {
        return tags;
    }

    public String getName() {
        return name;
    }
}