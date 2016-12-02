Logback
=======

Raven-Java provides `logback <http://logback.qos.ch/>`_
support for Raven. It provides an `Appender
<http://logback.qos.ch/apidocs/ch/qos/logback/core/Appender.html>`_
for logback to send the logged events to Sentry.

Installation
------------

If you want to use Maven you can install Raven-Logback as dependency:

.. sourcecode:: xml

    <dependency>
      <groupId>com.getsentry.raven</groupId>
      <artifactId>raven-logback</artifactId>
      <version>7.8.1</version>
    </dependency>

- :doc:`raven dependencies <raven>`
- `logback-core-1.1.2.jar
  <https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-core%7C1.1.2%7Cjar>`_
- `logback-classic-1.1.2.jar
  <https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-classic%7C1.1.2%7Cjar>`_
  will act as the implementation of slf4j (instead of slf4j-jdk14).

Usage
-----

In the ``logback.xml`` file set:

.. sourcecode:: xml

    <configuration>
      <appender name="Sentry" class="com.getsentry.raven.logback.SentryAppender">
        <dsn>___DSN___?options</dsn>
        <tags>tag1:value1,tag2:value2</tags>
        <!-- Optional, allows to select the ravenFactory -->
        <!--<ravenFactory>com.getsentry.raven.DefaultRavenFactory</ravenFactory>-->
      </appender>
      <root level="warn">
        <appender-ref ref="Sentry"/>
      </root>
    </configuration>

It's possible to add extra details to events captured by the logback
module thanks to the `marker system
<http://www.slf4j.org/faq.html#fatal>`_ which will add a tag
logback-Marker.  The `MDC system provided by logback
<http://logback.qos.ch/manual/mdc.html>`_ allows to add extra information
to the event.

Mapped Tags
-----------

By default all MDC parameters are sent under the Additional Data Tab. By
specify the extraTags parameter in your configuration file. You can
specify MDC keys to send as tags instead of including them in Additional
Data. This allows them to be filtered within Sentry.

.. sourcecode:: xml

    <extraTags>Environment,OS</extraTags>

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

    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.slf4j.MDC;
    import org.slf4j.MarkerFactory;

    public class MyClass {
        private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
        private static final Marker MARKER = MarkerFactory.getMarker("myMarker");

        void logSimpleMessage() {
            // This adds a simple message to the logs
            logger.info("This is a test");
        }

        void logWithTag() {
            // This adds a message with a tag to the logs named 'logback-Marker'
            logger.info(MARKER, "This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
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
