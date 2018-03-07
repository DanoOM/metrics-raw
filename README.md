# metrics-raw
Java Metrics Client Library to send RAW metrics to one or more datastores, heavily influenced by the dropwizard metrics library.  Sending RAW metric data can be extreemly costly under some conditions such as very has tps on cassandra/network/rendering layers, as such metrics-raw also enables sample based metrics (for example PercentileTimer will only report a 99th percentile if we have at least 100 samples).

# API
The api is heavily influenced by dropwizard's great metrics api, and uses vary similar concepts.

## MetricRegistry
The MetricRegistry is used to create/attach metric objects.  
The MetricRegsitry allows users to attach 'global' tags, which will be sent with every metric to the backend datastore.
The tags allow systems like grafana to query/aggregate metrics by tag.

The main metric methods of interest:

1. **addTag(tag,value)** - Attaches the tag to registry.
2. **timer(name, tags...)**  - Creates a new Timer instance with the specified name, the timer is started immediately. ** use sparingly **
3. **PercentileTimer(name,tags..)** - creates a timer, but will only emmit percentiles once n-number of samples have been reached (@todo consider replacing with 'bucketTimer')
3. **event(name)** - generates an event, that will be dispatched to any registered EventListener. ** use sparingly **
4. **eventBucket(name)** -- generate an event that is 'bucket' to n-samples
5. ** alert() ** -- generates an 'alert', essentially same as event, except is intended to explicitly generate an 'alert' (from a rendering layer). ** use sparingly ** 
6. **counter(name)** - get/create a counter with the associated name.
** use sparingly **
7. **scheduleGauge(name, interval, Gauge)** - schedules the gauge to be invoked on a periodic interval, (based on the last run)
8. **scheduleMaxGauge()** -- special case, gauges, will pull the 'gauge' at a higher frequency then is being reported, but will only report the maximum seen during that time. (@todo add scheduleMinGauge?)
9. **meter(name, interval, tags...)**
 

At a high level metrics can be constructed/referenced in 4 ways:

* metricRegistry.metric(String name) - create/get metric identified by name
* metricRegistry.metric(String name,string...tags) - create/get metric identified by name and tags (where tags is tagName/value..)
* metricRegistry.metric(String name,Map tags) - create/get metric identified by name and tags where tags is map of tagName/value

# Definitions

## Event
Events will be dispatched to any EventListener.  An Event will be triggered when any metric is modified, or when an user directly creates an event via the MetricRegistry.
** RAW Events should be used sparingly (less than 1 tps), as too many events can bog down the network, as well 'rendering' layers of that data (Use EventBuckets instead) **


## EventBucket
An EventBucket represents a set of Events, by default 100 events per bucket, this can changed via EventBucket.initBucketDataToReport(..)
EventBuckets by default will report the 99th percentile only, you can again modify this via initBucketDataToReport, which allows multiple percentiles, as well as min/max/ave/std.
When EventBuckets report, it the last event in the Bucket timestamp will be used for the entire bucket

## Alerts
Alerts are similar to Events, however rather then being emmited with metric name = 'metric-name', the name emmited will be 'alerts', and instead a tag 'alertName' == alertName will be generated.  This allows/ensures all alerts will be emmited with the same name, making alerts easier to track, and trigger off of.
** Alerts should be used sparingly (less than 1 tps), as too many alerts can bog down the network, as well 'rendering' layers of that data (Use @todo AlertBucket? or a 'timed'AlertBucket..where alerts are emmited after x-seconds rather then samples) **


## Gauge
Gauges are always scheduled, the users must implement getValue(), which returns either a Long, or a Double.  This is a functional interface.  Optionally users can elect to implement Map<String,String> getTags() if the gauge needs any tags associated with it.
Scheduling the 'same' gauge more then once, is not allowed, and the method will be behave idempotently.

## Meter
Meters are always schedule, and represent the 'rate of events' over time.  If you system generates very high tps rates, this can lead to data storage/throughput issues, using a meter (scheduled at 1second), can reduce storage/increase throughput for your metrics-system.


## Timer
Timers are automatically started when constructed.  calling stop on the timer will calculate the duration of time since startTime, any registered EventListeners will be updated.
The startTime/endTime reported will be accurate to the actual time the timer was started/end.
Timers when stopped can optionally accept additional tags.
** Timers should be used sparingly (less than 1 tps), as too many timers can bog down the network, as well as the 'rendering' layers of that data, (use PercentileTimers instead) **


## PercentileTimer
A Timer that reports percentiles.  These Timers will aggregate the data samples, and emit the percentiles requested.  This Timer is sample based, ie. To get the 99th percentile, it will require a buffer of 100 elements, and it will only report once the buffer is full.
By default PercetileTimers report p90, and p99 (out of a sample size of 100).
The reported start/endTime are not accurate, and do not represent the actual time the timer ran.  i.e. PercentileTimers are only good for tracking how long something ran.
 

## Counter
Counters, simply allows you increment/decrement.  When the counter is incremented/decremented a Event be sent to any registered EventListener.  Counters are NOT recommended for actual production use, as the 'graphing' system should be counting events.
For example counting http requests is redundant if you are generating events.
** Counters should be used sparingly (less than 1 tps), as too many timers can bog down the network, as well as the 'rendering' layers of that data, (use @todo add BucketCounter?) **


## EventListener
The EventListener, (aka Reporter) is responsible for sending events (metrics) to its associated backend datastore.
EventListers should be implemented Asynchronously, and should never block the calling the thread.
EventListeners implement one method: onEvent(Event).

# Conventions
Today when metrics are generated, the name of the metric will have a suffic added, conveying what type of metric it is.
1. timers get .timer appended
2. meter get .meter appended
3. gauge get .gauge added
4. counter get .counter added
5. alerts - All Alerts are emitted with the name alert, and tag: alertName=alertName
* Considiration: for PercentileTimers & eventBuckets, consider adding tag indicating the 'sample-count' (which will of course be 'constant' as this cannot vary).



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

