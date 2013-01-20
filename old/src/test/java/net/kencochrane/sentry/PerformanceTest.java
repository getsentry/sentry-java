package net.kencochrane.sentry;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

import java.util.Date;

/**
 * User: mphilpot
 * Date: 3/29/12
 */
public class PerformanceTest
{
    static final Logger log = Logger.getLogger(PerformanceTest.class);

    @Test
    public void testPerformance() throws InterruptedException
    {
        PropertyConfigurator.configure(getClass().getResource("/log4j_configuration.txt"));

        Date start = new Date();
        for(int i = 0; i < 100; i++)
        {
            log.info("Simple log message w/ no exception");
        }
        Date end = new Date();

        System.out.println(String.format("Simple test :: %d ms", end.getTime() - start.getTime()));

        Date startE = new Date();
        Exception e = new Exception("Test Exception");
        for(int i = 0; i < 100; i++ )
        {
            log.warn("Log message w/ exception", e);
        }
        Date endE = new Date();

        System.out.println(String.format("Exception test :: %d ms", endE.getTime() - startE.getTime()));

        // To see the messages get sent to the server
        //TimeUnit.SECONDS.sleep(30);
    }
}
