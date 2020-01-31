package org.dshops.metrics;

import java.util.Map;

public interface Timer {

    /** Add a tag to a running timer (todo should error out if timer already stopped) 
     * @param name tag name
     * @param value the tag value
     * @return Timer return reference to this timer
     * */
    public Timer addTag(String name, String value);

    /** calculates the time from the start time, also triggers an event for Listeners 
     * @return time in millis since started
     * */
    public long stop();

    public Timer start();

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners 
     * @param tags one or more tags/values pairs
     * @return time in millis since started
     * */
    public long stop(String... tags);

    /** todo This should error out, or 'not' update the duration on an already stopped timer. 
     * @param customTags map of tag/value pairs
     * @return time in millis since started
     * */
    public long stop(Map<String,String> customTags);
}
