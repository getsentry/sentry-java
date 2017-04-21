Manual Usage
============

**Note:** The following page provides examples on how to configure and use
Sentry directly. It is **highly recommended** that you use one of the provided
integrations instead if possible.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry</artifactId>
        <version>1.0.0</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry:1.0.0'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry" % "1.0.0"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry%7C1.0.0%7Cjar>`_.

Capture an Error
----------------

To report an event manually you need to construct a ``Sentry`` instance and use one
of the send methods it provides.

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.SentryClientFactory;

    public class MyClass {
        private static SentryClient sentry;

        public static void main(String... args) {
            // Creation of the client with a specific DSN
            String dsn = args[0];
            sentry = SentryClientFactory.sentryClient(dsn);

            // It is also possible to use the DSN detection system, which
            // will check the environment variable "SENTRY_DSN" and the Java
            // System Property "sentry.dsn".
            sentry = SentryClientFactory.sentryClient();
        }

        void logSimpleMessage() {
            // This sends a simple event to Sentry
            sentry.sendMessage("This is a test");
        }

        void logWithBreadcrumbs() {
            // Record a breadcrumb that will be sent with the next event(s),
            // by default the last 100 breadcrumbs are kept.
            Sentry.record(
                new BreadcrumbBuilder().setMessage("User made an action").build()
            );

            // This sends a simple event to Sentry
            sentry.sendMessage("This is a test");
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry
                sentry.sendException(e);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }
    }

Building More Complex Events
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For more complex messages, you'll need to build an ``Event`` with the
``EventBuilder`` class:

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.SentryClientFactory;
    import io.sentry.event.Event;
    import io.sentry.event.EventBuilder;
    import io.sentry.event.interfaces.ExceptionInterface;
    import io.sentry.event.interfaces.MessageInterface;

    public class MyClass {
        private static Sentry sentry;

        public static void main(String... args) {
            // Creation of the client with a specific DSN
            String dsn = args[0];
            sentry = SentryClientFactory.sentryClient(dsn);

            // It is also possible to use the DSN detection system, which
            // will check the environment variable "SENTRY_DSN" and the Java
            // System Property "sentry.dsn".
            sentry = SentryClientFactory.sentryClient();

            // Advanced: specify the sentryClientFactory used
            sentry = SentryClientFactory.sentryClient(new Dsn(dsn), "io.sentry.DefaultSentryClientFactory");
        }

        void logSimpleMessage() {
            // This sends an event to Sentry
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("This is a test")
                            .withLevel(Event.Level.INFO)
                            .withLogger(MyClass.class.getName());
            sentry.sendEvent(eventBuilder);
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry
                EventBuilder eventBuilder = new EventBuilder()
                                .withMessage("Exception caught")
                                .withLevel(Event.Level.ERROR)
                                .withLogger(MyClass.class.getName())
                                .withSentryInterface(new ExceptionInterface(e));
                sentry.sendEvent(eventBuilder);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }
    }

Static Access to Sentry
-----------------------

The most recently constructed ``Sentry`` instance is stored statically so it may
be used easily from anywhere in your application.

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.SentryClientFactory;

    public class MyClass {
        public static void main(String... args) {
            // Create a Sentry instance
            SentryClientFactory.sentryClient();
        }

        public somewhereElse() {
            // Use the Sentry instance statically. Note that we are
            // using the Class (and a static method) here
            Sentry.capture("Error message");

            // Or pass it a throwable
            Sentry.capture(new Exception("Error message"));

            // Or build an event yourself
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("Exception caught")
                            .withLevel(Event.Level.ERROR);
            Sentry.capture(eventBuilder.build());
        }

    }

Note that a Sentry instance *must* be created before you can use the ``Sentry.capture``
method, otherwise a ``NullPointerException`` (with an explanation) will be thrown.
