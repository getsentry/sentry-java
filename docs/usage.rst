Manual Usage
============

TODO: Note to prefer modules.

Installation
------------

TODO: Raw install

Capture an Error
----------------

It is possible to use the client manually rather than using a logging
framework in order to send messages to Sentry. It is not recommended to
use this solution as the API is more verbose and requires the developer to
specify the value of each field sent to Sentry:

.. sourcecode:: java

    import com.getsentry.raven.Raven;
    import com.getsentry.raven.RavenFactory;

    public class MyClass {
        private static Raven raven;

        public static void main(String... args) {
            // Creation of the client with a specific DSN
            String dsn = args[0];
            raven = RavenFactory.ravenInstance(dsn);

            // It is also possible to use the DSN detection system like this
            raven = RavenFactory.ravenInstance();
        }

        void logSimpleMessage() {
            // This adds a simple message to the logs
            raven.sendMessage("This is a test");
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This adds an exception to the logs
                raven.sendException(e);
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call that");
        }
    }

For more complex messages, it will be necessary to build an ``Event`` with the
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

            // It is also possible to use the DSN detection system like this
            raven = RavenFactory.ravenInstance();

            // Advanced: To specify the ravenFactory used
            raven = RavenFactory.ravenInstance(new Dsn(dsn), "com.getsentry.raven.DefaultRavenFactory");
        }

        void logSimpleMessage() {
            // This adds a simple message to the logs
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("This is a test")
                            .withLevel(Event.Level.INFO)
                            .withLogger(MyClass.class.getName());
            raven.runBuilderHelpers(eventBuilder); // Optional
            raven.sendEvent(eventBuilder.build());
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This adds an exception to the logs
                EventBuilder eventBuilder = new EventBuilder()
                                .withMessage("Exception caught")
                                .withLevel(Event.Level.ERROR)
                                .withLogger(MyClass.class.getName())
                                .withSentryInterface(new ExceptionInterface(e));
                raven.runBuilderHelpers(eventBuilder); // Optional
                raven.sendEvent(eventBuilder.build());
            }
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call that");
        }
    }

Static access
-------------

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
