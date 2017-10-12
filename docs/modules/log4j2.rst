Log4j 2.x
=========

The ``sentry-log4j2`` library provides `Log4j 2.x <https://logging.apache.org/log4j/2.x/>`_
support for Sentry via an `Appender
<https://logging.apache.org/log4j/2.x/log4j-core/apidocs/org/apache/logging/log4j/core/Appender.html>`_
that sends logged exceptions to Sentry. Once this integration is configured
you can *also* use Sentry's static API, :ref:`as shown on the usage page <usage_example>`,
in order to do things like record breadcrumbs, set the current user, or manually send
events.

The source can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry-log4j2>`_.

**Note:** The old ``raven-log4j2`` library is no longer maintained. It is highly recommended that
you `migrate <https://docs.sentry.io/clients/java/migration/>`_ to ``sentry-log4j2`` (which this
documentation covers). `Check out the migration guide <https://docs.sentry.io/clients/java/migration/>`_
for more information. If you are still using ``raven-log4j2`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/blob/raven-java-8.x/docs/modules/log4j2.rst>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry-log4j2</artifactId>
        <version>1.5.6</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry-log4j2:1.5.6'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry-log4j2" % "1.5.6"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-log4j2%7C1.5.6%7Cjar>`_.

Usage
-----

The following example configures a ``ConsoleAppender`` that logs to standard out
at the ``INFO`` level and a ``SentryAppender`` that logs to the Sentry server at
the ``WARN`` level. The ``ConsoleAppender`` is only provided as an example of
a non-Sentry appender that is set to a different logging threshold, like one you
may already have in your project.

Example configuration using the ``log4j2.xml`` format:

.. sourcecode:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <configuration status="warn" packages="org.apache.logging.log4j.core,io.sentry.log4j2">
        <appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n" />
            </Console>

            <Sentry name="Sentry" />
        </appenders>

        <loggers>
            <root level="INFO">
                <appender-ref ref="Console" />
                <!-- Note that the Sentry logging threshold is overridden to the WARN level -->
                <appender-ref ref="Sentry" level="WARN" />
            </root>
        </loggers>
    </configuration>

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``. :ref:`See the configuration page <configuration>` for ways you can do this.

Additional Data
---------------

It's possible to add extra data to events thanks to `the marker system
<https://logging.apache.org/log4j/2.x/manual/markers.html>`_
provided by Log4j 2.x.

Mapped Tags
~~~~~~~~~~~

By default all MDC parameters are stored under the "Additional Data" tab in Sentry. By
specifying the ``mdctags`` option in your configuration you can
choose which MDC keys to send as tags instead, which allows them to be used as
filters within the Sentry UI.

.. sourcecode:: java

    void logWithExtras() {
        // ThreadContext ("MDC") extras
        ThreadContext.put("Environment", "Development");
        ThreadContext.put("OS", "Linux");

        // This sends an event where the Environment and OS MDC values are set as additional data
        logger.error("This is a test");
    }

In Practice
-----------

.. sourcecode:: java

    import org.apache.logging.log4j.LogManager;
    import org.apache.logging.log4j.Logger;
    import org.apache.logging.log4j.Marker;
    import org.apache.logging.log4j.MarkerManager;

    public class MyClass {
        private static final Logger logger = LogManager.getLogger(MyClass.class);
        private static final Marker MARKER = MarkerManager.getMarker("myMarker");

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

        void logWithTag() {
            // This sends an event with a tag named 'log4j2-Marker' to Sentry
            logger.error(MARKER, "This is a test");
        }

        void logWithExtras() {
            // MDC extras
            ThreadContext.put("extra_key", "extra_value");
            // NDC extras are sent under 'log4j2-NDC'
            ThreadContext.push("Extra_details");
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
