package net.kencochrane.sentry;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 11:35 AM
 */

 // Import log4j classes.
 import org.apache.log4j.Logger;
 import org.apache.log4j.PropertyConfigurator;

 public class SentryExample {

   // Define a static logger variable so that it references the
   // Logger instance named "MyApp".
   static Logger logger = Logger.getLogger(SentryExample.class);

   public static void main(String[] args) {

     // PropertyConfigurator.
     PropertyConfigurator.configure(args[0]);

     logger.debug("Debug example");
     logger.error("Error example");
     logger.trace("Trace Example");
     logger.fatal("Fatal Example");
     logger.info("info Example");
     logger.warn("Warn Example");
   }
 }
