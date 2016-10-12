package org.dsh.metrics.generators;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.AggregatorFactory;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.TimeUnit;
import org.kairosdb.client.response.Queries;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.Results;

public class EventQueryGenerator extends Thread implements Runnable {
    final String[] eventNames;
    final Map<String,String[]> tags;
    final int tps;
    final AtomicBoolean exitFlag;
    private final HttpClient kairosDb;
    final private String prefix;
    final private AtomicInteger queryCount;
    final private AtomicInteger dataPointsRead;

    public EventQueryGenerator(HttpClient kairosDb,
                               String[] eventNames,
                               Map<String,String[]> tags,
                               int tps,
                               AtomicBoolean exitFlag,
                               String prefix,
                               AtomicInteger queryCount,
                               AtomicInteger dataPointsRead) {
        this.tps = tps;
        this.prefix = prefix + ".";
        this.eventNames = eventNames;
        this.tags = tags;
        this.exitFlag = exitFlag;
        this.kairosDb = kairosDb;
        this.queryCount = queryCount;
        this.dataPointsRead = dataPointsRead;
    }

    @Override
    public void run() {
        Random r = new Random();
        System.out.println("query generator started: targetTPS:"+tps);

        while(!this.exitFlag.get()) {
            long startTime = System.currentTimeMillis() + 1000;
            for (int j = 0; j < tps; j++) {
                if (tps < 1000) {
                    int sleepTime = 1000 / tps;
                    pause(r.nextInt(sleepTime));
                }

                String event = eventNames[r.nextInt(eventNames.length)];

                int minutes = r.nextInt(3) + 2; // lets assume users can only look at 2+ minutes of data.
                //if (r.nextInt(100) > 95) {
                //    minutes +=60;   // lets assume 5% queries are looking at a 1 hour period.
                //}
                QueryBuilder qb = QueryBuilder.getInstance();
                    qb.setStart(minutes, TimeUnit.MINUTES)
                      .addMetric(prefix+event)
                      .addAggregator(AggregatorFactory.createAverageAggregator(1, TimeUnit.MINUTES));
                try {
                    queryCount.incrementAndGet();
                    long st = System.currentTimeMillis();

                    QueryResponse resp = kairosDb.query(qb);
                    //System.out.println("query time (ms): " + (System.currentTimeMillis() - st));
                    if (resp.getStatusCode() != 200) {
                        System.out.println("error unexpected status code:" + resp.getStatusCode() + " BODY: " + resp.getBody());
                    }
                    List<Queries> queries = resp.getQueries();
                    for (Queries q: queries){
                        List<Results> results = q.getResults();
                        for (Results res: results) {
                            List<DataPoint> dataPoints = res.getDataPoints();
                            if (dataPoints != null && dataPoints.size() > 0) {
                               // System.out.println("("+event+") ("+minutes+") DataPoints returned: " + dataPoints.size());
                            }
                            dataPointsRead.incrementAndGet();
                        }
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
            // sleep for the remaining time of this 1 second period.
            long sleepTime = startTime - System.currentTimeMillis();
            pause(sleepTime);
        }
    }

    public void pause(long ms) {
        try {
            if (ms < 0){
                ms = 1;
            }
            Thread.sleep(ms);
        }
        catch(Exception e) {
            // swallow
        }
       }
}

