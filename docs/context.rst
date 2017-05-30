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
         *
         * Note that the ``Sentry.init`` method must be called before the static API
         * is used, otherwise a ``NullPointerException`` will be thrown.
         */
        public void staticAPIExample() {
            Sentry.init();

            // Set the current user in the context.
            Sentry.setUser(new UserBuilder().setUsername("user1").build());

            // Record a breadcrumb without having to look up the context instance manually.
            Sentry.record(
                new BreadcrumbBuilder().setMessage("User did something specific again!").build()
            );

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
            SentryClient sentryClient = SentryClientFactory.sentryClient();

            // Get the current context instance.
            Context context = sentryClient.getContext();

            // Set the current user in the context.
            context.setUser(
                new UserBuilder().setUsername("user1").build()
            );

            // Record a breadcrumb in the context.
            context.recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("User did something specific!").build()
            );

            // Send an event with the context data attached.
            sentryClient.sendMessage("New event message");

            // Clear the context, useful if you need to add hooks in a framework
            // to empty context between requests.
            context.clear();
        }
    }
