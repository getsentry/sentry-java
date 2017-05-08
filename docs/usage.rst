Manual Usage
============

**Note:** The following page provides examples on how to configure and use
Raven directly. It is **highly recommended** that you use one of the provided
integrations instead if possible.

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

Capture an Error
----------------

To report an event manually you need to construct a ``Raven`` instance and use one
of the send methods it provides.

.. sourcecode:: java

    import com.getsentry.raven.Raven;
    import com.getsentry.raven.RavenFactory;

    public class MyClass {
        private static Raven raven;

        public static void main(String... args) {
            // Creation of the client with a specific DSN
            String dsn = args[0];
            raven = RavenFactory.ravenInstance(dsn);

            // Or, if you don't provide a DSN,
            raven = RavenFactory.ravenInstance();

            // It is also possible to use the DSN detection system, which
            // will check the environment variable "SENTRY_DSN" and the Java
            // System Property "sentry.dsn".
            raven = RavenFactory.ravenInstance();
        }

        void logSimpleMessage() {
            // This sends a simple event to Sentry
            raven.sendMessage("This is a test");
        }

        void logWithBreadcrumbs() {
            // Record a breadcrumb that will be sent with the next event(s),
            // by default the last 100 breadcrumbs are kept.
            Breadcrumbs.record(
                new BreadcrumbBuilder().setMessage("User made an action").build()
            );

            // This sends a simple event to Sentry
            raven.sendMessage("This is a test");
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry
                raven.sendException(e);
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

    import com.getsentry.raven.Raven;
    import com.getsentry.raven.RavenFactory;
    import com.getsentry.raven.event.Event;
    import com.getsentry.raven.event.EventBuilder;
    import com.getsentry.raven.event.interfaces.ExceptionInterface;
    import com.getsentry.raven.event.interfaces.MessageInterface;

    public class MyClass {
        private static Raven raven;

        public static void main(String... args) {
            // Creation of the client with a specific DSN
            String dsn = args[0];
            raven = RavenFactory.ravenInstance(dsn);

            // It is also possible to use the DSN detection system, which
            // will check the environment variable "SENTRY_DSN" and the Java
            // System Property "sentry.dsn".
            raven = RavenFactory.ravenInstance();

            // Advanced: specify the ravenFactory used
            raven = RavenFactory.ravenInstance(new Dsn(dsn), "com.getsentry.raven.DefaultRavenFactory");
        }

        void logSimpleMessage() {
            // This sends an event to Sentry
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("This is a test")
                            .withLevel(Event.Level.INFO)
                            .withLogger(MyClass.class.getName());
            raven.sendEvent(eventBuilder);
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
                raven.sendEvent(eventBuilder);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }
    }

Static Access to Raven
----------------------

The most recently constructed ``Raven`` instance is stored statically so it may
be used easily from anywhere in your application.

.. sourcecode:: java

    import com.getsentry.raven.Raven;
    import com.getsentry.raven.RavenFactory;

    public class MyClass {
        public static void main(String... args) {
            // Create a Raven instance
            RavenFactory.ravenInstance();
        }

        public somewhereElse() {
            // Use the Raven instance statically. Note that we are
            // using the Class (and a static method) here
            Raven.capture("Error message");

            // Or pass it a throwable
            Raven.capture(new Exception("Error message"));

            // Or build an event yourself
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("Exception caught")
                            .withLevel(Event.Level.ERROR);
            Raven.capture(eventBuilder.build());
        }

    }

Note that a Raven instance *must* be created before you can use the ``Raven.capture``
method, otherwise a ``NullPointerException`` (with an explanation) will be thrown.
