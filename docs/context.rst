Context & Breadcrumbs
=====================

The Java SDK implements the idea of a "context" to support attaching additional
information to events, such as breadcrumbs. A context may refer to a single
request to a web framework, to the entire lifetime of an Android application,
or something else that better suits your application's needs.

There is no single definition of context that applies to every application,
for this reason a specific implementation must be chosen depending on what your
application does and how it is structured. By default Raven uses a
``ThreadLocalContextManager`` that maintains a single ``Context`` instance per thread.
This is useful for frameworks that use one thread per user request such as those based
on synchronous servlet APIs. Raven also installs a ``ServletRequestListener`` that will
clear the thread's context after each servlet request finishes.

Raven defaults to the ``SingletonContextManager`` on Android, which maintains a single
context instance for all threads for the lifetime of the application.

As of version ``8.0.3`` to override the ``ContextManager`` you will need to override
the ``getContextManager`` method in the ``DefaultRavenFactory``. A simpler API will likely
be provided in the future.

Using Breadcrumbs
-----------------

Breadcrumbs can be used to describe actions that occurred in your application leading
up to an event being sent. For example, whether external API requests were made,
or whether a user clicked on something in an Android application.

Once a Raven instance has been initialized, either via a logging framework or manually,
you can begin recording breadcrumbs. By default the last 100 breadcrumbs for a given
context instance will be stored and sent with future events.

.. sourcecode:: java

    import com.getsentry.raven.Raven;
    import com.getsentry.raven.context.Context;
    import com.getsentry.raven.event.BreadcrumbBuilder;
    import com.getsentry.raven.event.Breadcrumbs;
    import com.getsentry.raven.event.UserBuilder;

    public void example() {
        // Record a breadcrumb without having to look up the context instance manually
        Breadcrumbs.record(
            new BreadcrumbBuilder().setMessage("User did something specific again!").build()
        );

        // ... or retrieve and manipulate the context instance manually

        // Retrieve the stored Raven instance
        Raven raven = Raven.getStoredInstance();

        // Get the current context instance
        Context context = raven.getContext();

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