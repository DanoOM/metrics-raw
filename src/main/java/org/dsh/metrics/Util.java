package org.dsh.metrics;

import java.util.HashMap;
import java.util.Map;

class Util {

	public static Map<String,String> buildTags(final String...tags) {
		final Map<String,String> map = new HashMap<String,String>();
    	if (tags.length % 2 != 0) {
    		throw new IllegalArgumentException("corrupted tags arguments, must be name/value,name/value, tags provided not divisiable by 2!");
    	}
    	int iterations = tags.length / 2;
    	for (int i = 0; i < iterations; ) {
    		map.put(tags[0],tags[++i]);
    	}
    	return map;
	}
}
