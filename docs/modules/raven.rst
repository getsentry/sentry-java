java.util.logging
=================

The ``raven`` library provides a `java.util.logging Handler
<http://docs.oracle.com/javase/7/docs/api/java/util/logging/Handler.html>`_
that sends logged exceptions to Sentry.

The source for ``raven-java`` can be found `on Github
<https://github.com/getsentry/raven-java/tree/master/raven>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven</artifactId>
        <version>8.0.3</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven:8.0.3'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "com.getsentry.raven" % "raven" % "8.0.3"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven%7C8.0.3%7Cjar>`_.

Usage
-----

The following example configures a ``ConsoleHandler`` that logs to standard out
at the ``INFO`` level and a ``SentryHandler`` that logs to the Sentry server at
the ``WARN`` level. The ``ConsoleHandler`` is only provided as an example of
a non-Sentry appender that is set to a different logging threshold, like one you
may already have in your project.

Example configuration using the ``logging.properties`` format:

.. sourcecode:: ini

    # Enable the Console and Sentry handlers
    handlers=java.util.logging.ConsoleHandler,com.getsentry.raven.jul.SentryHandler

    # Set the default log level to INFO
    .level=INFO

    # Override the Sentry handler log level to WARNING
    com.getsentry.raven.jul.SentryHandler.level=WARNING

When starting your application, add the ``java.util.logging.config.file`` to
the system properties, with the full path to the ``logging.properties`` as
its value::

    $ java -Djava.util.logging.config.file=/path/to/app.properties MyClass

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``. See below for the two ways you can do this.

Configuration via Runtime Environment
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the most flexible method for configuring the ``SentryHandler``,
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
``SENTRY_EXTRATAGS``    ``sentry.extratags``    ``foo,bar,baz``                 Optional, provide tag names to be extracted from MDC
======================= ======================= =============================== ===========

Configuration via Static File
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can also configure everything statically within the ``logging.properties``
file itself. This is less flexible and not recommended because it's more difficult to change
the values when you run your application in different environments.

Example configuration in the ``logging.properties`` file:

.. sourcecode:: ini

    # Enable the Console and Sentry handlers
    handlers=java.util.logging.ConsoleHandler, com.getsentry.raven.jul.SentryHandler

    # Set the default log level to INFO
    .level=INFO

    # Override the Sentry handler log level to WARNING
    com.getsentry.raven.jul.SentryHandler.level=WARNING

    # Set Sentry DSN
    com.getsentry.raven.jul.SentryHandler.dsn=https://host:port/1?options

    # Optional, provide tags
    com.getsentry.raven.jul.SentryHandler.tags=tag1:value1,tag2:value2

    # Optional, provide release version of your application
    com.getsentry.raven.jul.SentryHandler.release=1.0.0

    # Optional, provide environment your application is running in
    com.getsentry.raven.jul.SentryHandler.environment=production

    # Optional, override the server name (rather than looking it up dynamically)
    com.getsentry.raven.jul.SentryHandler.serverName=server1

    # Optional, select the ravenFactory class
    com.getsentry.raven.jul.SentryHandler.ravenFactory=com.foo.RavenFactory

    # Optional, provide tag names to be extracted from MDC
    com.getsentry.raven.jul.SentryHandler.extraTags=foo,bar,baz

In Practice
-----------

.. sourcecode:: java

    import java.util.logging.Level;
    import java.util.logging.Logger;

    public class MyClass {
        private static final Logger logger = Logger.getLogger(MyClass.class.getName());

        void logSimpleMessage() {
            // This sends a simple event to Sentry
            logger.error(Level.INFO, "This is a test");
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

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry
                logger.error(Level.SEVERE, "Exception caught", e);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }
    }
