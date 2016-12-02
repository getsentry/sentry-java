Log4j
=====

Raven-Java provides `log4j <https://logging.apache.org/log4j/1.2/>`_
support for Raven. It provides an `Appender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html>`_
for log4j to send the logged events to Sentry.

The project can be found on github: `raven-java/raven-log4j
<https://github.com/getsentry/raven-java/tree/master/raven-log4j>`_

Installation
------------

If you want to use Maven you can install Raven-Log4j as dependency:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-log4j</artifactId>
        <version>7.8.1</version>
    </dependency>

If you manually want to manage your dependencies:

- :doc:`raven dependencies <raven>`
- `log4j-1.2.17.jar <https://search.maven.org/#artifactdetails%7Clog4j%7Clog4j%7C1.2.17%7Cjar>`_
- `slf4j-log4j12-1.7.7.jar
  <https://search.maven.org/#artifactdetails%7Corg.slf4j%7Cslf4j-log4j12%7C1.7.7%7Cjar>`_
  is recommended as the implementation of slf4j (instead of slf4j-jdk14).

Usage
-----

The following configuration (``logging.properties``) gets you started for
logging with log4j and Sentry:

.. sourcecode:: ini

    log4j.rootLogger=WARN, SentryAppender
    log4j.appender.SentryAppender=com.getsentry.raven.log4j.SentryAppender
    log4j.appender.SentryAppender.dsn=___DSN___
    log4j.appender.SentryAppender.tags=tag1:value1,tag2:value2
    # Optional, allows to select the ravenFactory
    #log4j.appender.SentryAppender.ravenFactory=com.getsentry.raven.DefaultRavenFactory

Alternatively in the ``log4j.xml`` file set:

.. sourcecode:: xml

    <appender name="sentry" class="com.getsentry.raven.log4j.SentryAppender">
      <param name="dsn" value="___DSN___"/>
      <filter class="org.apache.log4j.varia.LevelRangeFilter">
        <param name="levelMin" value="WARN"/>
      </filter>
    </appender>

It's possible to add extra details to events captured by the Log4j module
thanks to both `the MDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html>`_
and `the NDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html>`_
systems provided by Log4j are usable, allowing to attach extras
information to the event.

Practical Example
-----------------

.. sourcecode:: java

    import org.apache.log4j.Logger;
    import org.apache.log4j.MDC;
    import org.apache.log4j.NDC;

    public class MyClass {
        private static final Logger logger = Logger.getLogger(MyClass.class);

        void logSimpleMessage() {
            // This adds a simple message to the logs
            logger.info("This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
            // NDC extras are sent under 'log4J-NDC'
            NDC.push("Extra_details");
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

Mapped Tags
-----------

By default all MDC parameters are sent under the Additional Data Tab. By
specify the ``extraTags`` parameter in your configuration file. You can
specify MDC keys to send as tags instead of including them in Additional
Data. This allows them to be filtered within Sentry.

.. sourcecode:: java

    log4j.appender.SentryAppender.extraTags=Environment,OS
        void logWithExtras() {
            // MDC extras
            MDC.put("Environment", "Development");
            MDC.put("OS", "Linux");

            // This adds a message with extras and MDC keys declared in extraTags as tags to Sentry
            logger.info("This is a test");
        }

Asynchronous Logging
--------------------

It is not recommended to attempt to set up ``SentryAppender`` within an
`AsyncAppender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html>`_.
While this is a common solution to avoid blocking the current thread until
the event is sent to Sentry, it is recommended to rely instead on the
asynchronous connection provided by Raven.
