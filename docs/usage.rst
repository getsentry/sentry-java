Manual Usage
============

**Note:** The following page provides examples on how to configure and use
Sentry directly. It is **highly recommended** that you use one of the
:ref:`provided integrations <integrations>` if possible. Once the integration
is configured you can *also* use Sentry's static API, as shown below,
in order to do things like record breadcrumbs, set the current user, or manually
send events.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry</artifactId>
        <version>1.5.2</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry:1.5.2'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry" % "1.5.2"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry%7C1.5.2%7Cjar>`_.

Capture an Error
----------------

.. _usage_example:

To report an event manually you need to initialize a ``SentryClient``. It is recommended
that you use the static API via the ``Sentry`` class, but you can also construct and manage
your own ``SentryClient`` instance. An example of each style is shown below:

.. sourcecode:: java

    import io.sentry.context.Context;
    import io.sentry.event.BreadcrumbBuilder;
    import io.sentry.event.UserBuilder;

    public class MyClass {
        private static SentryClient sentry;

        public static void main(String... args) {
            /*
            It is recommended that you use the DSN detection system, which
            will check the environment variable "SENTRY_DSN", the Java
            System Property "sentry.dsn", or the "sentry.properties" file
            in your classpath. This makes it easier to provide and adjust
            your DSN without needing to change your code. See the configuration
            page for more information.
            */
            Sentry.init();

            // You can also manually provide the DSN to the ``init`` method.
            String dsn = args[0];
            Sentry.init(dsn);

            /*
            It is possible to go around the static ``Sentry`` API, which means
            you are responsible for making the SentryClient instance available
            to your code.
            */
            sentry = SentryClientFactory.sentryClient();

            MyClass myClass = new MyClass();
            myClass.logWithStaticAPI();
            myClass.logWithInstanceAPI();
        }

        /**
         * An example method that throws an exception.
         */
        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }

        /**
         * Examples using the (recommended) static API.
         */
        void logWithStaticAPI() {
            // Note that all fields set on the context are optional. Context data is copied onto
            // all future events in the current context (until the context is cleared).

            // Record a breadcrumb in the current context. By default the last 100 breadcrumbs are kept.
            Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("User made an action").build()
            );

            // Set the user in the current context.
            Sentry.getContext().setUser(
                new UserBuilder().setEmail("hello@sentry.io").build()
            );

            // Add extra data to future events in this context.
            Sentry.getContext().addExtra("extra", "thing");

            // Add an additional tag to future events in this context.
            Sentry.getContext().addTag("tagName", "tagValue");

            /*
            This sends a simple event to Sentry using the statically stored instance
            that was created in the ``main`` method.
            */
            Sentry.capture("This is a test");

            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry using the statically stored instance
                // that was created in the ``main`` method.
                Sentry.capture(e);
            }
        }

        /**
         * Examples that use the SentryClient instance directly.
         */
        void logWithInstanceAPI() {
            // Retrieve the current context.
            Context context = sentry.getContext();

            // Record a breadcrumb in the current context. By default the last 100 breadcrumbs are kept.
            context.recordBreadcrumb(new BreadcrumbBuilder().setMessage("User made an action").build());

            // Set the user in the current context.
            context.setUser(new UserBuilder().setEmail("hello@sentry.io").build());

            // This sends a simple event to Sentry.
            sentry.sendMessage("This is a test");

            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry.
                sentry.sendException(e);
            }
        }
    }

Building More Complex Events
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For more complex messages, you'll need to build an ``Event`` with the
``EventBuilder`` class:

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.event.Event;
    import io.sentry.event.EventBuilder;
    import io.sentry.event.interfaces.ExceptionInterface;

    public class MyClass {
        public static void main(String... args) {
            Sentry.init();
        }

        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }

        void logSimpleMessage() {
            // This sends an event to Sentry.
            EventBuilder eventBuilder = new EventBuilder()
                            .withMessage("This is a test")
                            .withLevel(Event.Level.INFO)
                            .withLogger(MyClass.class.getName());

            // Note that the *unbuilt* EventBuilder instance is passed in so that
            // EventBuilderHelpers are run to add extra information to your event.
            Sentry.capture(eventBuilder);
        }

        void logException() {
            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry.
                EventBuilder eventBuilder = new EventBuilder()
                                .withMessage("Exception caught")
                                .withLevel(Event.Level.ERROR)
                                .withLogger(MyClass.class.getName())
                                .withSentryInterface(new ExceptionInterface(e));

                // Note that the *unbuilt* EventBuilder instance is passed in so that
                // EventBuilderHelpers are run to add extra information to your event.
                Sentry.capture(eventBuilder);
            }
        }
 }

Automatically Enhancing Events
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can also implement an ``EventBuilderHelper`` that is able to automatically
enhance outgoing events.

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.SentryClient;
    import io.sentry.event.EventBuilder;
    import io.sentry.event.helper.EventBuilderHelper;

    public class MyClass {
        public void myMethod() {
            SentryClient client = Sentry.getStoredClient();

            EventBuilderHelper myEventBuilderHelper = new EventBuilderHelper() {
                @Override
                public void helpBuildingEvent(EventBuilder eventBuilder) {
                    eventBuilder.withMessage("Overwritten by myEventBuilderHelper!");
                }
            };

            // Add an ``EventBuilderHelper`` to the current client instance. Note that
            // this helper will process *all* future events.
            client.addBuilderHelper(myEventBuilderHelper);

            // Send an event to Sentry. During construction of the event the message
            // body will be overwritten by ``myEventBuilderHelper``.
            Sentry.capture("Hello, world!");
        }
    }
