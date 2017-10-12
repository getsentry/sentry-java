Log4j 1.x
=========

The ``sentry-log4j`` library provides `Log4j 1.x <https://logging.apache.org/log4j/1.2/>`_
support for Sentry via an `Appender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html>`_
that sends logged exceptions to Sentry. Once this integration is configured
you can *also* use Sentry's static API, :ref:`as shown on the usage page <usage_example>`,
in order to do things like record breadcrumbs, set the current user, or manually send
events.

The source can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry-log4j>`_.

**Note:** The old ``raven-log4j`` library is no longer maintained. It is highly recommended that
you `migrate <https://docs.sentry.io/clients/java/migration/>`_ to ``sentry-log4j`` (which this
documentation covers). `Check out the migration guide <https://docs.sentry.io/clients/java/migration/>`_
for more information. If you are still using ``raven-log4j`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/blob/raven-java-8.x/docs/modules/log4j.rst>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry-log4j</artifactId>
        <version>1.5.6</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry-log4j:1.5.6'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry-log4j" % "1.5.6"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-log4j%7C1.5.6%7Cjar>`_.

Usage
-----

The following examples configure a ``ConsoleAppender`` that logs to standard out
at the ``INFO`` level and a ``SentryAppender`` that logs to the Sentry server at
the ``WARN`` level. The ``ConsoleAppender`` is only provided as an example of
a non-Sentry appender that is set to a different logging threshold, like one you
may already have in your project.

Example configuration using the ``log4j.properties`` format:

.. sourcecode:: ini

    # Enable the Console and Sentry appenders
    log4j.rootLogger=INFO, Console, Sentry

    # Configure the Console appender
    log4j.appender.Console=org.apache.log4j.ConsoleAppender
    log4j.appender.Console.layout=org.apache.log4j.PatternLayout
    log4j.appender.Console.layout.ConversionPattern=%d{HH:mm:ss.SSS} [%t] %-5p: %m%n

    # Configure the Sentry appender, overriding the logging threshold to the WARN level
    log4j.appender.Sentry=io.sentry.log4j.SentryAppender
    log4j.appender.Sentry.threshold=WARN

Alternatively, using  the ``log4j.xml`` format:

.. sourcecode:: xml

    <?xml version="1.0" encoding="UTF-8" ?>
    <!DOCTYPE log4j:configuration SYSTEM "log4j.dtd">
    <log4j:configuration debug="true"
    	xmlns:log4j='http://jakarta.apache.org/log4j/'>

        <!-- Configure the Console appender -->
    	<appender name="Console" class="org.apache.log4j.ConsoleAppender">
    	    <layout class="org.apache.log4j.PatternLayout">
    		<param name="ConversionPattern"
    		       value="%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n" />
    	    </layout>
    	</appender>

        <!-- Configure the Sentry appender, overriding the logging threshold to the WARN level -->
        <appender name="Sentry" class="io.sentry.log4j.SentryAppender">
            <!-- Override the Sentry handler log level to WARN -->
            <filter class="org.apache.log4j.varia.LevelRangeFilter">
                <param name="levelMin" value="WARN" />
            </filter>
        </appender>

        <!-- Enable the Console and Sentry appenders, Console is provided as an example
             of a non-Sentry logger that is set to a different logging threshold -->
        <root level="INFO">
            <appender-ref ref="Console" />
            <appender-ref ref="Sentry" />
        </root>
    </log4j:configuration>

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``. :ref:`See the configuration page <configuration>` for ways you can do this.

Additional Data
---------------

It's possible to add extra data to events thanks to `the MDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html>`_
and `the NDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html>`_
systems provided by Log4j 1.x.

Mapped Tags
~~~~~~~~~~~

By default all MDC parameters are stored under the "Additional Data" tab in Sentry. By
specifying the ``mdctags`` option in your configuration you can
choose which MDC keys to send as tags instead, which allows them to be used as
filters within the Sentry UI.

.. sourcecode:: java

    void logWithExtras() {
        // MDC extras
        MDC.put("Environment", "Development");
        MDC.put("OS", "Linux");

        // This sends an event where the Environment and OS MDC values are set as additional data
        logger.error("This is a test");
    }

In Practice
-----------

.. sourcecode:: java

    import org.apache.log4j.Logger;
    import org.apache.log4j.MDC;
    import org.apache.log4j.NDC;

    public class MyClass {
        private static final Logger logger = Logger.getLogger(MyClass.class);

        void logSimpleMessage() {
            // This sends a simple event to Sentry
            logger.error("This is a test");
        }

        void logWithBreadcrumbs() {
            // Record a breadcrumb that will be sent with the next event(s),
            // by default the last 100 breadcrumbs are kept.
            Sentry.record(
                new BreadcrumbBuilder().setMessage("User made an action").build()
            );

            // This sends a simple event to Sentry
            logger.error("This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
            // NDC extras are sent under 'log4J-NDC'
            NDC.push("Extra_details");
            // This sends an event with extra data to Sentry
            logger.error("This is a test");
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry
                logger.error("Exception caught", e);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }
    }

Asynchronous Logging
--------------------

Sentry uses asynchronous communication by default, and so it is unnecessary
to use an `AsyncAppender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html>`_.
