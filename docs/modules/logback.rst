Logback
=======

The ``sentry-logback`` library provides `Logback <http://logback.qos.ch/>`_
support for Sentry via an `Appender
<http://logback.qos.ch/apidocs/ch/qos/logback/core/Appender.html>`_
that sends logged exceptions to Sentry. Once this integration is configured
you can *also* use Sentry's static API, :ref:`as shown on the usage page <usage_example>`,
in order to do things like record breadcrumbs, set the current user, or manually send
events.

The source can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry-logback>`_.

**Note:** The old ``raven-logback`` library is no longer maintained. It is highly recommended that
you `migrate <https://docs.sentry.io/clients/java/migration/>`_ to ``sentry-logback`` (which this
documentation covers). `Check out the migration guide <https://docs.sentry.io/clients/java/migration/>`_
for more information. If you are still using ``raven-logback`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/blob/raven-java-8.x/docs/modules/logback.rst>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry-logback</artifactId>
        <version>1.5.5</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry-logback:1.5.5'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry-logback" % "1.5.5"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-logback%7C1.5.5%7Cjar>`_.

Usage
-----

The following example configures a ``ConsoleAppender`` that logs to standard out
at the ``INFO`` level and a ``SentryAppender`` that logs to the Sentry server at
the ``WARN`` level. The ``ConsoleAppender`` is only provided as an example of
a non-Sentry appender that is set to a different logging threshold, like one you
may already have in your project.

Example configuration using the ``logback.xml`` format:

.. sourcecode:: xml

    <configuration>
        <!-- Configure the Console appender -->
        <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>

        <!-- Configure the Sentry appender, overriding the logging threshold to the WARN level -->
        <appender name="Sentry" class="io.sentry.logback.SentryAppender">
            <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
                <level>WARN</level>
            </filter>
        </appender>

        <!-- Enable the Console and Sentry appenders, Console is provided as an example
             of a non-Sentry logger that is set to a different logging threshold -->
        <root level="INFO">
            <appender-ref ref="Console" />
            <appender-ref ref="Sentry" />
        </root>
    </configuration>

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``.   :ref:`See the configuration page <setting_the_dsn>` for ways you can do this.

Additional Data
---------------

It's possible to add extra data to events thanks to `the MDC system provided by Logback
<http://logback.qos.ch/manual/mdc.html>`_.

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

    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.slf4j.MDC;
    import org.slf4j.MarkerFactory;

    public class MyClass {
        private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
        private static final Marker MARKER = MarkerFactory.getMarker("myMarker");

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
            // This sends an event with a tag named 'logback-Marker' to Sentry
            logger.error(MARKER, "This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
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
