package org.dsh.metrics.tests;

import org.dsh.metrics.EventListener;
import org.dsh.metrics.Timer;
import org.dsh.metrics.listeners.KairosDBListener;
import org.testng.annotations.Test;

public class KairosListenerTest extends BaseListenerTest {

    @Override
    public EventListener getListener() {
        return new KairosDBListener("http://wdc-tst-masapp-001:8080",
                                    "root",
                                    "root");
    }


    @Override
    @Test
    public void timerWithTagsTest() {
        try {
            reg.addEventListener(getListener());
            for (int i = 0; i < 100 ; i++) {
                Timer t = reg.timerWithTags("test.testTimer")
                             .addTag("cust", "customer-x")
                             .build();
                if (i % 5 == 0) {
                    Thread.sleep(100);
                }
                else {
                    Thread.sleep(10);
                }
                t.stop();
            }

            Thread.sleep(10000);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

}
