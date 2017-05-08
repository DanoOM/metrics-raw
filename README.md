# metrics-raw
Java Metrics Client Library to send RAW metrics to one or more datastores, heavily influenced by the dropwizard metrics library.

# API
The api is heavily influenced by dropwizard's great metrics api, and uses vary similar concepts.

## MetricRegistry
The MetricRegistry is used to create/attach metric objects.  
The MetricRegsitry allows users to attach 'global' tags, which will be sent with every metric to the backend datastore.
The tags allow systems like grafana to query/aggregate metrics by tag.

The main methods of interest:

1. **addTag(tag,value)** - Attaches the tag to registry.
2. **timer(name, tags...)**  - Creates a new Timer instance with the specified name, the timer is started immediately.
3. **event(name)** - generates an event, that will be dispatched to any registered EventListener.
4. **counter(name)** - get/create a counter with the associated name.
5. **scheduleGauge(name, interval, Gauge)** - schedules the gauge to be invoked on a periodic interval, (based on the last run)
6. **meter(name, interval, tags...)** 

At a high level metrics can be constructed/referenced in 4 ways:

* metricRegistry.metric(String name) - create/get metric identified by name
* metricRegistry.metric(String name,string...tags) - create/get metric identified by name and tags (where tags is tagName/value..)
* metricRegistry.metric(String name,Map tags) - create/get metric identified by name and tags where tags is map of tagName/value


## Event
We have no meters in this api, since we send the raw metric, as such we simply have 'events' in place of meters.
Events will be dispatched to any EventListener.  An Event will be triggered when any metric is modified, or when an user directly creates an event via the MetricRegistry.

## Gauge
Gauges are always scheduled, the users must implement getValue(), which returns either a Long, or a Double.  This is a functional interface.  Optionally users can elect to implement Map<String,String> getTags() if the gauge needs any tags associated with it.
Scheduling the 'same' gauge more then once, is not allowed, and the method will be behave idempotently.

## Meter
Meters are always schedule, and represent the 'rate of events' over time.  If you system generates very high tps rates, this can lead to data storage/throughput issues, using a meter (scheduled at 1second), can reduce storage/increase throughput for your metrics-system.


## Timer
Timers are automatically started when constructed.  calling stop on the timer will calculate the duration of time since startTime, any registered EventListeners will be updated.
The startTime/endTime reported will be accurate to the actual time the timer was started/end.
Timers when stopped can optionally accept additional tags.

## PercentileTimer
A Timer that reports percentiles.  These Timers will aggregate the data samples, and emit the percentiles requested.  This Timer is sample based, ie. To get the 99th percentile, it will require a buffer of 100 elements, and it will only report once the buffer is full.
By default PercetileTimers report p90, and p99 (out of a sample size of 100).
The reported start/endTime are not accurate, and do not represent the actual time the timer ran.  i.e. PercentileTimers are only good for tracking how long something ran. 

## Counter
Counters, simply allows you increment/decrement.  When the counter is incremented/decremented a Event be sent to any registered EventListener.  Counters are NOT recommended for actual production use, as the 'graphing' system should be counting events.
For example counting http requests is redundant if you are generating events.

## EventListener
The EventListener, (aka Reporter) is responsible for sending events (metrics) to its associated backend datastore.
EventListers should be implemented Asynchronously, and should never block the calling the thread.
EventListeners implement one method: onEvent(Event).

## Provided Listeners
At this point we only have 2 Listeners implemented

1. ConsoleListener - Simply logs the event to the console.
2. KairosListener - sends events to a KairosDB.

## KairosDBListener -- WARNING: MOVED TO NEW github project (metrics-raw-kairosdb)
Users should not need to interact with the KairosDBListener, except during it construction, the following 3 constructors are provided.

1. KairosDBListener(String conectString, String un, String pd, MetricRegistry registry)
2. KairosDBListener(String conectString, String un, String pd, MetricRegistry registry, batchSize)
3. KairosDBListener(String conectString, String un, String pd, MetricRegistry registry, int batchSize, int bufferSize, long offerTimeMillis)

Where: 
* connectString: http://kairoshost:port
* un: username (not used atm).
* pd: password (not used atm).
* registry: (the parent metric registry, used to determine tags for metric statics: (stats.xyz).
* batchSize: batch size to use when uploading statistics.
* bufferSize: internal buffer used to store metrics. (default: 5000)
* offerTimeMillis: the amount of time to wait before dropping a metric data point if the buffer is full.  (should be zero in prod, this is primarly used for testing).

The kairosdb listener will upload 4 statistics to kairos at 1 minute intervals
1. metricsraw.stats.data.count - Count of data points uploaded to the server.
2. metricsraw.stats.data.dropped - The number of datapoints dropped due buffer being full.
3. metricsraw.stats.http.errors - The number of errors encountered when attempted to upload metrics.
4. metrisraw.stats.http.count - The actual number of http requests to upload metrics.
The 4 metrics will be tagged, with the following tags:
host
namespace
application
appType


* NOTE: KairosDB currently only stores ms precision, and can only store 1 event per ms of the same Event with the same NAME/TAG-SET.  

## Options are being considered: 

1. Generating an idx tag, for when multple events of the same type occur on the same ms.
2. In addition the kairosListener does not 'group' events efficiently when dispatching the server.
3. Grouping/Bining - Raw Data can generate a lot of data/network traffic, (recent Kairosdb client has added some capability for 'bucketing data': see  http://kairosdb.github.io/docs/build/html/restapi/Overview.html

# This is still a work in progress.

