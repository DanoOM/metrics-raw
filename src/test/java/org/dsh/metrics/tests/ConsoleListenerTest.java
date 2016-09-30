package org.dsh.metrics.tests;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.dsh.metrics.EventListener;
import org.dsh.metrics.Timer;
import org.dsh.metrics.listeners.ConsoleListener;
import org.testng.annotations.Test;


public class ConsoleListenerTest extends BaseListenerTest {

    @Override
    public EventListener getListener() {
        PrintStream ps = new PrintStream(new ByteArrayOutputStream());
        return new ConsoleListener(ps, 100, 500);
    }

    @Override
    @Test
    public void counterTest() {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            EventListener listener = new ConsoleListener(new PrintStream(stream), 100, 500);
            reg.counter("counter1");
            reg.addEventListener(listener);
            for (int i = 0; i < 10_000;i++) {
                reg.counter("counter1").increment();
            }
            pause(listener, 0, 5000);

            String results = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = results.split(System.lineSeparator());
            int length = lines.length;
            assertTrue(length == 10_000);
            for (int i = 0; i < length; i++) {
                String[] cols = lines[i].split(" ");
                assertTrue(cols.length == 4); // TS NAME TAGS VALUE
                assertTrue(cols[1].equals("counter1"));
                assertTags(cols[2], reg.getTags());
                assertTrue(Integer.parseInt(cols[3]) == i+1);
            }

        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void assertTags(String tagString, Map<String,String> tags2) {
        String[] pairs = tagString.split(",");
        Map<String,String> tags1 = new HashMap<String,String>();
        for (String p : pairs) {
            String[] kv = p.split("=");
            tags1.put(kv[0],kv[1]);
        }
        assertEquals("tag are not equal", tags1,tags2);
    }

    private void assertTags(String tagString, Map<String,String> tags2, Map<String,String> tags3) {
        String[] pairs = tagString.split(",");
        Map<String,String> tags1 = new HashMap<String,String>();
        for (String p : pairs) {
            String[] kv = p.split("=");
            tags1.put(kv[0],kv[1]);
        }
        Map<String,String> tags = new HashMap<>();
        tags.putAll(tags2);
        tags.putAll(tags3);
        assertEquals("tag are not equal", tags1,tags);
    }

    @Override
    @Test
    public void timerWithTagsTest() {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            EventListener listener = new ConsoleListener(new PrintStream(stream), 100, 500);
            reg.addEventListener(listener);
            Timer t = reg.timerWithTags("testTimer")
                         .addTag("cust", "customer-x")
                         .build();
            Thread.sleep(1000);
            t.stop();
            pause(listener, 0, 1000);
            String results = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = results.split(System.lineSeparator());
            assertEquals("unexpected number of timer events!", 1, lines.length);
            String[] cols = lines[0].split(" ");
            assertEquals("maltformed event..", cols.length, 4);
            assertTrue(cols[1].equals("testTimer"));
            assertTags(cols[2], reg.getTags(), t.getTags());
            assertTrue(cols[2].equals("host=host-1.xyz.org,cust=customer-x,dc=dataCenter1"));
            assertTrue(Integer.parseInt(cols[3]) >= 1000 && Integer.parseInt(cols[3]) < 1002); // could be off slightly..
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @Test
    public void timerTest() {
        try {
            reg.addEventListener(getListener());
            Timer t = reg.timer("testTimer");
            Thread.sleep(1000);
            t.stop();
            Thread.sleep(10000);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }


}
