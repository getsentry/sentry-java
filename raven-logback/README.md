## Logback ##
"Logback is intended as a successor to the popular log4j project, picking up where log4j leaves off.[...]
Logback brings a very large number of improvements over log4j, big and small. [...]
Keep in mind that logback is conceptually very similar to log4j as both projects were founded by the same developer. If you are already familiar with log4j, you will quickly feel at home using logback. If you like log4j, you will probably love logback."

Logback project: http://logback.qos.ch/
Reasons to prefer logback over log4j: http://logback.qos.ch/reasonsToSwitch.html

## Maven dependencies ##
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
            <version>1.0-SNAPSHOT</version>
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

## Appender configuration ##
Example of configuration in src/main/resources/logback.xml

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
