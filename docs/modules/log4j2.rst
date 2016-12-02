Log4j 2
=======

Raven-Java provides `Log4j 2 <https://logging.apache.org/log4j/2.x/>`_
support for Raven. It provides an `Appender
<https://logging.apache.org/log4j/2.x/log4j-core/apidocs/org/apache/logging/log4j/core/Appender.html>`_
for Log4j 2 to send the logged events to Sentry.

The project can be found on github: `raven-java/raven-log4j2
<https://github.com/getsentry/raven-java/tree/master/raven-log4j2>`_

Installation
------------

If you want to use Maven you can install Raven-Log4j2 as dependency:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-log4j2</artifactId>
        <version>7.8.1</version>
    </dependency>

If you manually want to manage your dependencies:

- :doc:`raven dependencies <raven>`
- `log4j-api-2.1.jar
  <https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-api%7.8.1%7Cjar>`_
- `log4j-core-2.0.jar
  <https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-core%7.8.1%7Cjar>`_
- `log4j-slf4j-impl-2.1.jar
  <http://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-slf4j-impl%7.8.1%7Cjar>`_
  is recommended as the implementation of slf4j (instead of slf4j-jdk14).

Usage
-----

The following configuration (``log4j2.xml``) gets you started for
logging with log4j2 and Sentry:

.. sourcecode:: java

    <?xml version="1.0" encoding="UTF-8"?>
    <configuration status="warn" packages="org.apache.logging.log4j.core,com.getsentry.raven.log4j2">
      <appenders>
        <Raven name="Sentry">
          <dsn>
            ___DSN___?options
          </dsn>
          <tags>
            tag1:value1,tag2:value2
          </tags>
          <!--
            Optional, allows to select the ravenFactory
          -->
          <!--
          <ravenFactory>
            com.getsentry.raven.DefaultRavenFactory
          </ravenFactory>
          -->
        </Raven>
      </appenders>

      <loggers>
        <root level="all">
          <appender-ref ref="Sentry"/>
        </root>
      </loggers>
    </configuration>

It's possible to add extra details to events captured by the Log4j 2
module thanks to the `marker system
<https://logging.apache.org/log4j/2.x/manual/markers.html>`_ which will
add a tag log4j2-Marker.  Both the MDC and the NDC systems provided by
Log4j 2 are usable, allowing to attach extras information to the event.

Mapped Tags
-----------

By default all MDC parameters are sent under the Additional Data Tab. By
specify the ``extraTags`` parameter in your configuration file. You can
specify MDC keys to send as tags instead of including them in Additional
Data. This allows them to be filtered within Sentry.

.. sourcecode:: xml

    <extraTags>
      Environment,OS
    </extraTags>

.. sourcecode:: java

    void logWithExtras() {
        // MDC extras
        MDC.put("Environment", "Development");
        MDC.put("OS", "Linux");

        // This adds a message with extras and MDC keys declared in extraTags as tags to Sentry
        logger.info("This is a test");
    }

Practical Example
-----------------

.. sourcecode:: java

    import org.apache.logging.log4j.LogManager;
    import org.apache.logging.log4j.Logger;
    import org.apache.logging.log4j.Marker;
    import org.apache.logging.log4j.MarkerManager;

    public class MyClass {
        private static final Logger logger = LogManager.getLogger(MyClass.class);
        private static final Marker MARKER = MarkerManager.getMarker("myMarker");

        void logSimpleMessage() {
            // This adds a simple message to the logs
            logger.info("This is a test");
        }

        void logWithTag() {
            // This adds a message with a tag to the logs named 'log4j2-Marker'
            logger.info(MARKER, "This is a test");
        }

        void logWithExtras() {
            // MDC extras
            ThreadContext.put("extra_key", "extra_value");
            // NDC extras are sent under 'log4j2-NDC'
            ThreadContext.push("Extra_details");
            // This adds a message with extras to the logs
            logger.info("This is a test");
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This adds an exception to the logs
                logger.error("Exception caught", e);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call that");
        }
    }
