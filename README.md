# metrics-raw
Java Metrics Client Library to send RAW metrics to one or more datastores, heavily influenced by the dropwizard metrics library.

# API
The api is heavily influenced by dropwizard's great metrics api, and uses vary similar concepts.

## MetricRegistry
The MetricRegistry is used to create/attach metric objects.  
The MetricRegsitry allows users to attach 'global' tags, which will be sent with every metric to the backend datastore.
The tags allow allow systems like grafana to query/aggregate metrics by tag, or not.

The basic methods of interest:
1. ** addTag(tag,value) ** - Attaches the tag to regsitry.
2. ** timer(name) **  - Creates a new Timer instance with the specified name, the timer is started immediatly.
3. ** timerWithTags(name) ** - returns a Builder, allowing tags to be added via addTag(tag,name), followed by build to create and start the timer.
4. ** event(name) ** - generates an event, that will be dispatched to any registered EventListener.
5. ** eventWithTags(name) ** - returns a Builder, allowing tags to be added, calling build() will dispatch the event to any registered EventListener
6. ** counter(name) ** - get/create a counter with the associated name.
7. ** counterWithTags(name) ** - returns a builder, where you can attach tags.  The builder will either return a new Counter, or return a pre-existing counter if the name/tags match.
8. ** scheduleGauge(name, interval,Gauge) ** - schedules the gauge to be invoked on a periodic interval, (based on the last run) 

## Event
We have no meters in this api, since we send the raw metric, as such we simply have 'events' in place of meters.
Events will be dispatched to any EventListener.  An Event will be triggered when any metric is modified, or when an user directly creates an event via the MetricRegistry.

## Gauge
Gauges are always scheduled, the users must implement getValue(), which returns either a Long, or a Double.  This is a functional interface.  Optionally users can elect to implement Map<String,String> getTags() if the gauge needs any tags associated with it.
Scheduling the 'same' gauge more then once, is not allowed, and the method will be behave idempotently.

## Timer
Timers are automatically started when constructed.  calling stop on the timer will calculate the duration of time since startTime, any registered EventListeners will be updated.

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
2. KairosListener - sends events to a KairosDB endpoint.

* NOTE: KairosDB currently only stores ms precision, and can only store 1 event per ms of the same Event with the same NAME/TAG-SET.  

## Options are being considered: 

1. Generating an idx tag, for when multple events of the same type occur on the same ms.
2. In addition the kairosListener does not 'group' events efficiently when dispatching the server.
3. Grouping/Bining - Raw Data can generate a lot of data/network traffic, (recent Kairosdb client has added some capability for 'bucketing data': see  http://kairosdb.github.io/docs/build/html/restapi/Overview.html

# This is still a work in progress.

