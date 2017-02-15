Log4j
=====

The ``raven-log4j`` library provides `log4j <https://logging.apache.org/log4j/1.2/>`_
support for Raven via an `Appender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html>`_
that sends logged exceptions to Sentry.

The project can be found on Github: `raven-java/raven-log4j
<https://github.com/getsentry/raven-java/tree/master/raven-log4j>`_

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-log4j</artifactId>
        <version>7.8.2</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-log4j:7.8.2'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "com.getsentry.raven" % "raven-log4j" % "7.8.2"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-log4j%7C7.8.2%7Cjar>`_.


Usage
-----

The following examples configure a ``ConsolerAppender`` that logs to standard out
at the ``INFO`` level and a ``SentryAppender`` that logs to the Sentry server at
the ``WARN`` level.

Example configuration using the ``log4j.properties`` format:

.. sourcecode:: ini

    # Enable the Console and Sentry appenders, Console is provided as an example
    # of a non-Raven logger that is set to a different logging threshold
    log4j.rootLogger=INFO, Console, Sentry

    # Configure the Console appender
    log4j.appender.Console=org.apache.log4j.ConsoleAppender
    log4j.appender.Console.layout=org.apache.log4j.PatternLayout
    log4j.appender.Console.layout.ConversionPattern=%d{HH:mm:ss.SSS} [%t] %-5p: %m%n

    # Configure the Sentry appender, overriding the logging threshold to the WARN level
    log4j.appender.Sentry=com.getsentry.raven.log4j.SentryAppender
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
        <appender name="Sentry" class="com.getsentry.raven.log4j.SentryAppender">
            <!-- Override the Sentry handler log level to WARN -->
            <filter class="org.apache.log4j.varia.LevelRangeFilter">
                <param name="levelMin" value="WARN"/>
            </filter>
        </appender>

        <!-- Enable the Console and Sentry appenders, Console is provided as an example
             of a non-Raven logger that is set to a different logging threshold -->
        <root level="INFO>
            <appender-ref ref="Console" />
            <appender-ref ref="Sentry" />
        </root>
    </log4j:configuration>

Next, you'll need to configure your DSN (client key) and optionally other values such as
``environment`` and ``release``. See below for the two ways you can do this.


Configuration via runtime environment
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the most flexible method to configure the ``SentryAppender``,
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
``SENTRY_EXTRA_TAGS``   ``sentry.extratags``    ``foo,bar,baz``                 Optional, provide tag names to be extracted from MDC when using SLF4J
======================= ======================= =============================== ===========

Configuration via static file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can also configure everything statically within the ``log4j.properties`` or ``log4j.xml``
file itself. This is less flexible and not recommended because it's more difficult to change
the values when you run your application in different environments.

Example configuration in the ``log4j.properties`` file:

.. sourcecode:: ini

    # Set Sentry DSN
    log4j.appender.SentryAppender.dsn=https://host:port/1?options

    # Optional, provide release version of your application
    log4j.appender.SentryAppender.release=1.0.0

    # Optional, provide environment your application is running in
    log4j.appender.SentryAppender.environment=production

    # Optional, override the server name (rather than looking it up dynamically)
    log4j.appender.SentryAppender.serverName=server1

    # Optional, select the ravenFactory class
    log4j.appender.SentryAppender.ravenFactory=com.foo.RavenFactory

    # Optional, provide tags
    log4j.appender.SentryAppender.tags=tag1:value1,tag2:value2

    # Optional, provide tag names to be extracted from MDC when using SLF4J
    log4j.appender.SentryAppender.extraTags=foo,bar,baz

Additional data
---------------

It's possible to add extra data to events thanks to `the MDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html>`_
and `the NDC
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html>`_
systems provided by Log4j.

In practice
-----------

.. sourcecode:: java

    import org.apache.log4j.Logger;
    import org.apache.log4j.MDC;
    import org.apache.log4j.NDC;

    public class MyClass {
        private static final Logger logger = Logger.getLogger(MyClass.class);

        void logSimpleMessage() {
            // This adds a simple message to the logs
            logger.error("This is a test");
        }

        void logWithExtras() {
            // MDC extras
            MDC.put("extra_key", "extra_value");
            // NDC extras are sent under 'log4J-NDC'
            NDC.push("Extra_details");
            // This adds a message with extras to the logs
            logger.error("This is a test");
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

By default all MDC parameters are stored under the "Additional Data" tab in Sentry. By
specifying the ``extraTags`` parameter in your configuration file you can
choose which MDC keys to send as tags instead of including them as "Additional
Data." This allows them to be used as filters within the Sentry UI.

.. sourcecode:: java
§
    void logWithExtras() {
        // MDC extras
        MDC.put("Environment", "Development");
        MDC.put("OS", "Linux");

        // This adds a message with extras and MDC keys declared in extraTags as tags to Sentry
        logger.error("This is a test");
    }

Asynchronous Logging
--------------------

Raven uses asynchronous communication by default, and so it is unnecessary
to use an `AsyncAppender
<https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html>`_.
