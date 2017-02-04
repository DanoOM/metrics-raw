package org.dshops.metrics.generators;

import org.dshops.metrics.MetricRegistry;
import org.dshops.metrics.Timer;
import org.dshops.metrics.listeners.ConsoleListener;
import org.dshops.metrics.listeners.KairosIndexedDBListener;

public class Test {

    public static void main(String[] args) {
        try{
        MetricRegistry mr = new MetricRegistry.Builder("dshops","metrics", "test").withHost("DanHost").build();
        mr.addEventListener(new ConsoleListener(System.out));
        mr.addEventListener(new KairosIndexedDBListener("http://wdc-tst-masapp-001:8080", "root", "root", mr));
     //   mr.event("testEvent", 22);
      //  mr.counter("testCounter").increment();
     //   mr.counter("testCounter").increment();
     /*   for (int i = 0; i < 300000; i++){
            try{Thread.sleep(5);}
            catch(Exception e){};
            mr.counter("testCounter").increment();
        }*/
        System.out.println("");
        //mr.timer("", "","").addTag("", "").stop("");
        Timer t = mr.timer("testTimer",
                 "url",
                 "url","foo",
                 "sometag","x");
        Thread.sleep(100);
        t.stop();

        t = mr.timer("testTimer",
                           "url","foo",
                           "sometag","x");
        System.out.println("exit..");
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

}
