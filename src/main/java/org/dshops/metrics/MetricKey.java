package org.dshops.metrics;

import java.util.Map;

// A Metrickey represents a unique hash for metric+tagset
// obviously if the tagset changes afterwards it will not reflected.
//consideration: if remove counters from the system
//and 'allow' users to create duplicate gauges, this and their internal
//maps can be removed.
public class MetricKey {
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