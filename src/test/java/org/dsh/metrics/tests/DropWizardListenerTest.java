package org.dsh.metrics.tests;

import java.util.Random;

import org.dsh.metrics.EventListener;
import org.dsh.metrics.Timer;
import org.dsh.metrics.listeners.DropWizardListener;
import org.testng.annotations.Test;

public class DropWizardListenerTest extends BaseListenerTest {

    @Override
    public EventListener newListener() {
        DropWizardListener listener = new DropWizardListener("wdc-tst-metrics-001.openmarket.com", 2003, 20);
        listener.addMap("dsh-metrics.test.testTimer","dsh-metrics.test.testTimer.{host}");

        //listener.addMap("*.*.*.{host}", dropWizardName);

        listener.addMap("sqs.*.*", "sqs.{host}.*.{httpMethod}");
        return listener;
    }


    @Override
    @Test
    public void timerWithTagsTest() {
        try {
            reg.addEventListener(newListener());
            Random r = new Random();
            for (int i = 0; i < 5000 ; i++) {
                Timer t = reg.timerWithTags("testTimer")
                             .addTag("cust", "customer-x")
                             .build();
                if (i % 5 == 0) {
                    Thread.sleep(r.nextInt(100));
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
