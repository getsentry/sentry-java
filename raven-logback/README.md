# Raven-Logback
A [logback](http://logback.qos.ch/) appender passing messages along to [Sentry](http://www.getsentry.com/).

## Maven dependencies
````xml
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.2</version>
        </dependency>
		
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.0.7</version>
        </dependency>

        <dependency>
            <groupId>net.kencochrane</groupId>
            <artifactId>raven-logback</artifactId>
            <version>2.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
````

## Using the logback appender

````java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Example {
    public static Logger logger = LoggerFactory.getLogger(Example.class);

    public static void main(String[] args) {
        logger.info("Hello World");
        logger.trace("Hello World!");
        logger.debug("How are you today?");
        logger.info("I am fine.");
        logger.warn("I love programming.");
        logger.error("I am programming.");
    }

}
````

## Appender configuration
Example of configuration in src/test/resources/sentryappender.logback.xml

````xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
 
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %boldRed(%-5level) %logger{36} - %msg%n</pattern>
 	</encoder>
  </appender>
   
  <appender name="SENTRY" class="net.kencochrane.raven.logback.SentryAppender">
    <sentryDsn>http://2d0fd0e8c0d546279c7115b563bdf60f:3429a634a51b4b5b937be06d83bc6c97@localhost:9000/1</sentryDsn>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
  	
    <root level="debug">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="SENTRY" />
    </root>
</configuration>
````
