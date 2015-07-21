Configuration
=============

Raven's configuration happens via the DSN value.  This guides you through
the configuration that applies generally and configuration that is
specific to the Java client.

Connection and Protocols
------------------------

It is possible to send events to Sentry over different protocols,
depending on the security and performance requirements.

HTTP
````

The most common way send events to Sentry is through HTTP, this can be
done by using a DSN of this form::

    http://<public>:<private>@sentryserver/<project>

This is unavailable for Hosted Sentry which requires HTTPS.

HTTPS
`````

It is possible to use an encrypted connection to Sentry using HTTPS::

    ___DSN___

HTTPS (naive)
`````````````

If the certificate used over HTTPS is a wildcard certificate (which is not
handled by every version of Java), and the certificate isn't added to the
truststore, it is possible to add a protocol setting to tell the client to
be naive and ignore the hostname verification::

    naive+___DSN___

Options
-------

It is possible to enable some options by adding data to the query string
of the DSN::

    ___DSN___?option1=value1&option2&option3=value3

Some options do not require a value, just being declared signifies that
the option is enabled.

Async Settings
``````````````

Async connection:
    In order to avoid performance issues due to a large amount of logs
    being generated or a slow connection to the Sentry server, an
    asynchronous connection is set up, using a low priority thread pool to
    submit events to Sentry.

    To disable the async mode, add ``raven.async=false`` to the DSN::

        ___DSN___?raven.async=false

Graceful Shutdown (advanced):
    In order to shutdown the asynchronous connection gracefully, a
    ``ShutdownHook`` is created. This could lead to memory leaks in an
    environment where the life cycle of Raven doesn't match the life cycle
    of the JVM.

    An example would be in a JEE environment where the application using
    Raven could be deployed and undeployed regularly.

    To avoid this behaviour, it is possible to disable the graceful
    shutdown. This might lead to some log entries being lost if the log
    application doesn't shut down the Raven instance nicely.

    The option to do so is ``raven.async.gracefulshutdown``::

        ___DSN___?raven.async.gracefulshutdown=false

Queue and Thread Settings
`````````````````````````

Queue size (advanced):
    The default queue used to store the not yet processed events doesn't
    have a limit. Depending on the environment (if the memory is sparse)
    it is important to be able to control the size of that queue to avoid
    memory issues.

    It is possible to set a maximum with the option ``raven.async.queuesize``::

        ___DSN__?raven.async.queuesize=100

    This means that if the connection to the Sentry server is down, only
    the 100 most recent events will be stored and processed as soon as the
    server is back up.

Threads count (advanced):
    By default the thread pool used by the async connection contains one
    thread per processor available to the JVM (more threads wouldn't be
    useful).

    It's possible to manually set the number of threads (for example if
    you want only one thread) with the option ``raven.async.threads``::

        ___DSN___?raven.async.threads=1

Threads priority (advanced):
    As in most cases sending logs to Sentry isn't as important as an
    application running smoothly, the threads have a `minimal priority
    <http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY>`_.

    It is possible to customise this value to increase the priority of
    those threads with the option ``raven.async.priority``::

        ___DSN___?raven.async.priority=10

Inapp Classes Settings
``````````````````````

Sentry differentiate ``in_app`` stack frames (which are directly related
to your application) and the "not ``in_app``" ones. This difference is
visible in the Sentry web interface where only the ``in_app`` frames are
displayed by default.

Same frame as enclosing exception:
    Raven can use the ``in_app`` system to hide frames in the context of
    chained exceptions.

    Usually when a ``StackTrace`` is printed, the result looks like this::

        HighLevelException: MidLevelException: LowLevelException
                at Main.a(Main.java:13)
                at Main.main(Main.java:4)
        Caused by: MidLevelException: LowLevelException
                at Main.c(Main.java:23)
                at Main.b(Main.java:17)
                at Main.a(Main.java:11)
                ... 1 more
        Caused by: LowLevelException
                at Main.e(Main.java:30)
                at Main.d(Main.java:27)
                at Main.c(Main.java:21)
                ... 3 more

    Some frames are replaced by the ... N more line as they are the same
    frames as in the enclosing exception.

    To enable a similar behaviour from raven use the
    ``raven.stacktrace.hidecommon`` option::

        ___DSN___?raven.stacktrace.hidecommon

Hide frames based on the class name:
    Raven can also mark some frames as ``in_app`` based on the name of the
    class.

    This can be used to hide parts of the stacktrace that are irrelevant
    to the problem for example the stack frames in the ``java.util``
    package will not help determining what the problem was and will just
    create a longer stacktrace.

    Currently this is not configurable and some packages are ignored by default:

    * com.sun.*
    * java.*
    * javax.*
    * org.omg.*
    * sun.*
    * junit.*
    * com.intellij.rt.*

Transmission Settings
`````````````````````

Compression:
    By default the content sent to Sentry is compressed and encoded in
    base64 before being sent. This operation allows to send a smaller
    amount of data for each event. However compressing and encoding the
    data adds a CPU and memory overhead which might not be useful if the
    connection to Sentry is fast and reliable.

    Depending on the limitations of the project (ie: a mobile application
    with a limited connection, Sentry hosted on an external network), it
    can be interesting to compress the data beforehand or not.

    It's possible to manually enable/disable the compression with the
    option ``raven.compression``::

        ___DSN___?raven.compression=false

Timeout (advanced):
    To avoid blocking the thread because of a connection taking too much
    time, a timeout can be set by the connection.

    By default the connection will set up its own timeout, but it's
    possible to manually set one with ``raven.timeout`` (in milliseconds)::

        ___DSN___?raven.timeout=10000
