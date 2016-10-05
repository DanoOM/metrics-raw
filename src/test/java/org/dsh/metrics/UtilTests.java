package org.dsh.metrics;

import static org.testng.AssertJUnit.assertEquals;

import java.util.Map;

import org.testng.annotations.Test;

public class UtilTests {
    @Test
    public void buildTagsTest(){
        Map<String,String> tags = Util.buildTags("tag1","val1");
        assertEquals(tags.size(),1);
        assertEquals(tags.get("tag1"),"val1");

        tags = Util.buildTags("tag1","val1","tag2","val2");
        assertEquals(tags.size(),2);
        assertEquals(tags.get("tag1"),"val1");
        assertEquals(tags.get("tag2"),"val2");

        tags = Util.buildTags("tag1","val1","tag2","val2", "tag3", "val3");
        assertEquals(tags.size(), 3);
        assertEquals(tags.get("tag1"),"val1");
        assertEquals(tags.get("tag2"),"val2");
        assertEquals(tags.get("tag3"),"val3");
    }

}
