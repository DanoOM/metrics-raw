package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;

class Util {

	public static Map<String,String> buildTags(final String...tags) {
		final Map<String,String> map = new HashMap<String,String>();
    	if (tags.length % 2 != 0) {
    		throw new IllegalArgumentException("corrupted tags arguments, must be name/value,name/value, tags provided not divisiable by 2!");
    	}
    	for (int i = 0; i < tags.length; ) {
    		map.put(tags[i++],tags[i++]);
    	}
    	return map;
	}
}
