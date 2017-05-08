Logback
=======

The ``raven-logback`` library provides `Logback <http://logback.qos.ch/>`_
support for Raven via an `Appender
<http://logback.qos.ch/apidocs/ch/qos/logback/core/Appender.html>`_
that sends logged exceptions to Sentry.

The source can be found `on Github
<https://github.com/getsentry/raven-java/tree/master/raven-logback>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-logback</artifactId>
        <version>8.0.3</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-logback:8.0.3'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "com.getsentry.raven" % "raven-logback" % "8.0.3"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-logback%7C8.0.3%7Cjar>`_.

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
        <appender name="Sentry" class="com.getsentry.raven.logback.SentryAppender">
            <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
                <level>WARN</level>
            </filter>
        </appender>

        <!-- Enable the Console and Sentry appenders, Console is provided as an example
             of a non-Raven logger that is set to a different logging threshold -->
        <root level="INFO">
            <appender-ref ref="Console" />
            <appender-ref ref="Sentry" />
        </root>
    </configuration>

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``. See below for the two ways you can do this.

Configuration via Runtime Environment
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the most flexible method for configuring the ``SentryAppender``,
because it can be easily changed based on the environment you run your
application in.

The following can be set as System Environment variables:

.. sourcecode:: shell

    SENTRY_EXAMPLE=xxx java -jar app.jar

Or as Java System Properties:

.. sourcecode:: shell

    java -Dsentry.example=xxx -jar app.jar

Configuration parameters follow:

======================= ======================= =============================== ===========
Environment variable    Java System Property    Example value                   Description
======================= ======================= =============================== ===========
``SENTRY_DSN``          ``sentry.dsn``          ``https://host:port/1?options`` Your Sentry DSN (client key), if left blank Raven will no-op
``SENTRY_RELEASE``      ``sentry.release``      ``1.0.0``                       Optional, provide release version of your application
``SENTRY_ENVIRONMENT``  ``sentry.environment``  ``production``                  Optional, provide environment your application is running in
``SENTRY_SERVERNAME``   ``sentry.servername``   ``server1``                     Optional, override the server name (rather than looking it up dynamically)
``SENTRY_RAVENFACTORY`` ``sentry.ravenfactory`` ``com.foo.RavenFactory``        Optional, select the ravenFactory class
``SENTRY_TAGS``         ``sentry.tags``         ``tag1:value1,tag2:value2``     Optional, provide tags
``SENTRY_EXTRA_TAGS``   ``sentry.extratags``    ``foo,bar,baz``                 Optional, provide tag names to be extracted from MDC
======================= ======================= =============================== ===========

Configuration via Static File
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can also configure everything statically within the ``logback.xml``
file itself. This is less flexible and not recommended because it's more difficult to change
the values when you run your application in different environments.

Example configuration in the ``logback.xml`` file:

.. sourcecode:: xml

    <configuration>
        <!-- Configure the Console appender -->
        <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>

        <!-- Configure the Sentry appender, overriding the logging threshold to the WARN level -->
        <appender name="Sentry" class="com.getsentry.raven.logback.SentryAppender">
            <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
                <level>WARN</level>
            </filter>

            <!-- Set Sentry DSN -->
            <dsn>https://host:port/1?options</dsn>

            <!-- Optional, provide release version of your application -->
            <release>1.0.0</release>

            <!-- Optional, provide environment your application is running in -->
            <environment>production</environment>

            <!-- Optional, override the server name (rather than looking it up dynamically) -->
            <serverName>server1</serverName>

            <!-- Optional, select the ravenFactory class -->
            <ravenFactory>com.foo.RavenFactory</ravenFactory>

            <!-- Optional, provide tags -->
            <tags>tag1:value1,tag2:value2</tags>

            <!-- Optional, provide tag names to be extracted from MDC -->
            <extraTags>foo,bar,baz</extraTags>
        </appender>

        <!-- Enable the Console and Sentry appenders, Console is provided as an example
             of a non-Raven logger that is set to a different logging threshold -->
        <root level="INFO">
            <appender-ref ref="Console" />
            <appender-ref ref="Sentry" />
        </root>
    </configuration>

Additional Data
---------------

It's possible to add extra data to events thanks to `the MDC system provided by Logback
<http://logback.qos.ch/manual/mdc.html>`_.

Mapped Tags
~~~~~~~~~~~

By default all MDC parameters are stored under the "Additional Data" tab in Sentry. By
specifying the ``extraTags`` parameter in your configuration file you can
choose which MDC keys to send as tags instead, which allows them to be used as
filters within the Sentry UI.

.. sourcecode:: xml

    <extraTags>Environment,OS</extraTags>

.. sourcecode:: java

    void logWithExtras() {
        // MDC extras
        MDC.put("Environment", "Development");
        MDC.put("OS", "Linux");

        // This sends an event where the Environment and OS MDC values are set as tags
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
            Breadcrumbs.record(
                new BreadcrumbBuilder().setMessage("User made an action").build()
            );

            // This sends a simple event to Sentry
            logger.error("This is a test");
        }

        void logWithTag() {
            // This sends an event with a tag named 'logback-Marker' to Sentry
            logger.info(MARKER, "This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
            // This sends an event with extra data to Sentry
            logger.info("This is a test");
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
