package org.dshops.metrics;

import java.util.Map;

public interface Timer {

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    public Timer addTag(String name, String value);

    /** calculates the time from the starttime, also triggers an event for Listeners */
    public long stop();

    public Timer start();

    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    public long stop(String... tags);

    /** todo This should error out, or 'not' update the duration on an already stopped timer. */
    public long stop(Map<String,String> customTags);
}
