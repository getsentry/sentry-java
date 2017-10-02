java.util.logging
=================

The ``sentry`` library provides a `java.util.logging Handler
<http://docs.oracle.com/javase/7/docs/api/java/util/logging/Handler.html>`_
that sends logged exceptions to Sentry. Once this integration is configured
you can *also* use Sentry's static API, :ref:`as shown on the usage page <usage_example>`,
in order to do things like record breadcrumbs, set the current user, or manually send
events.

The source for ``sentry`` can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry>`_.

**Note:** The old ``raven`` library is no longer maintained. It is highly recommended that
you `migrate <https://docs.sentry.io/clients/java/migration/>`_ to ``sentry`` (which this
documentation covers). `Check out the migration guide <https://docs.sentry.io/clients/java/migration/>`_
for more information. If you are still using ``raven`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/blob/raven-java-8.x/docs/modules/raven.rst>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry</artifactId>
        <version>1.5.4</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry:1.5.4'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry" % "1.5.4"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry%7C1.5.4%7Cjar>`_.

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
    handlers=java.util.logging.ConsoleHandler,io.sentry.jul.SentryHandler

    # Set the default log level to INFO
    .level=INFO

    # Override the Sentry handler log level to WARNING
    io.sentry.jul.SentryHandler.level=WARNING

When starting your application, add the ``java.util.logging.config.file`` to
the system properties, with the full path to the ``logging.properties`` as
its value::

    $ java -Djava.util.logging.config.file=/path/to/app.properties MyClass

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``. :ref:`See the configuration page <configuration>` for ways you can do this.

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
            Sentry.record(
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
