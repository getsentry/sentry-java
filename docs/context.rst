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

Using Breadcrumbs
-----------------

Breadcrumbs can be used to describe actions that occurred in your application leading
up to an event being sent. For example, whether external API requests were made,
or whether a user clicked on something in an Android application.

Once a Sentry instance has been initialized, either via a logging framework or manually,
you can begin recording breadcrumbs. By default the last 100 breadcrumbs for a given
context instance will be stored and sent with future events.

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.context.Context;
    import io.sentry.event.BreadcrumbBuilder;
    import io.sentry.event.Breadcrumbs;
    import io.sentry.event.UserBuilder;

    public void example() {
        // Record a breadcrumb without having to look up the context instance manually
        Sentry.record(
            new BreadcrumbBuilder().setMessage("User did something specific again!").build()
        );

        // ... or retrieve and manipulate the context instance manually

        // Retrieve the stored Sentry instance
        SentryClient sentryClient = getStoredClient();

        // Get the current context instance
        Context context = sentryClient.getContext();

        // Set the current User in the context
        context.setUser(
            new UserBuilder().setUsername("user1").build()
        );

        // Record a breadcrumb in the context
        context.recordBreadcrumb(
            new BreadcrumbBuilder().setMessage("User did something specific!").build()
        );

        // Clear the context, useful if you need to add hooks in a framework
        // to empty context between requests
        context.clear()
    }
