Context & Breadcrumbs
=====================

The Java SDK implements the idea of a "context" to support attaching additional
information to events, such as breadcrumbs. A context may refer to a single
request to a web framework, to the entire lifetime of an Android application,
or something else that better suits your application's needs.

There is no single definition of context that applies to every application,
for this reason a specific implementation must be chosen depending on what your
application does and how it is structured. By default Sentry uses a
``ThreadLocalContextManager`` that maintains a single ``Context`` instance per thread.
This is useful for frameworks that use one thread per user request such as those based
on synchronous servlet APIs. Sentry also installs a ``ServletRequestListener`` that will
clear the thread's context after each servlet request finishes.

Sentry defaults to the ``SingletonContextManager`` on Android, which maintains a single
context instance for all threads for the lifetime of the application.

To override the ``ContextManager`` you will need to override the ``getContextManager``
method in the ``DefaultSentryClientFactory``. A simpler API will likely be provided in
the future.

Usage
-----

Breadcrumbs can be used to describe actions that occurred in your application leading
up to an event being sent. For example, whether external API requests were made,
or whether a user clicked on something in an Android application. By default the last
100 breadcrumbs per context will be stored and sent with future events.

The user can be set per context so that you know who was affected by each event.

Once a ``SentryClient`` instance has been initialized you can begin setting state in
the current context.

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.context.Context;
    import io.sentry.event.BreadcrumbBuilder;
    import io.sentry.event.UserBuilder;

    public class MyClass {

        /**
         * Examples using the (recommended) static API.
         */
        public void staticAPIExample() {
            // Manually initialize the static client, you may also pass in a DSN and/or
            // SentryClientFactory to use. Note that the client will attempt to automatically
            // initialize on the first use of the static API, so this isn't strictly necessary.
            Sentry.init();

            // Note that all fields set on the context are optional. Context data is copied onto
            // all future events in the current context (until the context is cleared).

            // Set the current user in the context.
            Sentry.getContext().setUser(
                new UserBuilder().setUsername("user1").build()
            );

            // Record a breadcrumb in the context.
            Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("User did something specific again!").build()
            );

            // Add extra data to future events in this context.
            Sentry.getContext().addExtra("extra", "thing");

            // Add an additional tag to future events in this context.
            Sentry.getContext().addTag("tagName", "tagValue");

            // Send an event with the context data attached.
            Sentry.capture("New event message");

            // Clear the context, useful if you need to add hooks in a framework
            // to empty context between requests.
            Sentry.clearContext();
        }

        /**
         * Examples that use the SentryClient instance directly.
         */
        public void instanceAPIExample() {
            // Create a SentryClient instance that you manage manually.
            SentryClient sentryClient = SentryClientFactory.sentryClient();

            // Get the current context instance.
            Context context = sentryClient.getContext();

            // Note that all fields set on the context are optional. Context data is copied onto
            // all future events in the current context (until the context is cleared).

            // Set the current user in the context.
            context.setUser(
                new UserBuilder().setUsername("user1").build()
            );

            // Record a breadcrumb in the context.
            context.recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("User did something specific!").build()
            );

            // Add extra data to future events in this context.
            context.addExtra("extra", "thing");

            // Add an additional tag to future events in this context.
            context.addTag("tagName", "tagValue");

            // Send an event with the context data attached.
            sentryClient.sendMessage("New event message");

            // Clear the context, useful if you need to add hooks in a framework
            // to empty context between requests.
            context.clear();
        }
    }
