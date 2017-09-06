.. _configuration:

Configuration
=============

**Note:** Sentry's library and framework integration documentation explains how to to do
the initial Sentry configuration for each of the supported integrations. The configuration
below can be used in combination with any of the integrations *once you set Sentry up with
the integration*. Please check :ref:`the integration documentation <integrations>` before
you attempt to do any advanced configuration.

.. _setting_the_dsn:

Setting the DSN (Data Source Name)
----------------------------------

The DSN is the first and most important thing to configure because it tells the SDK where
to send events. You can find your project's DSN in the "Client Keys" section of your "Project Settings"
in Sentry. It can be configured in multiple ways. Explanations of the :ref:`configuration methods are
detailed below <configuration_methods>`.

In a properties file on your filesystem or classpath (defaults to ``sentry.properties``):

.. sourcecode:: properties

    dsn=https://public:private@host:port/1

Via the Java System Properties:

.. sourcecode:: shell

    java -Dsentry.dsn=https://public:private@host:port/1 -jar app.jar

Via a System Environment Variable:

.. sourcecode:: shell

    SENTRY_DSN=https://public:private@host:port/1 java -jar app.jar

In code:

.. sourcecode:: java

    import io.sentry.Sentry;

    Sentry.init("https://public:private@host:port/1");

.. _configuration_methods:

Configuration methods
---------------------

There are multiple ways to configure the Java SDK, but all of them take the same options.
See below for how to use each configuration method and how the option names might
differ between them.

Configuration via properties file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Java SDK can be configured via a `.properties file <https://en.wikipedia.org/wiki/.properties>`_
that is located on the filesystem or on your application's classpath. By default the SDK will look for
a ``sentry.properties`` file in the current directory or in the root of your classpath. You can override
the location of the properties file by using either the ``sentry.properties.file`` Java System Property
or the ``SENTRY_PROPERTIES_FILE`` System Environment Variable.

Because this file is often bundled with your application, the values cannot be changed easily once your
application has been packaged. For this reason, the properties file is useful for setting defaults or options
that you don't expect to change often. The properties file is the last place checked for
each option value, so runtime configuration (described below) will override it if available.

Option names in the property file exactly match the examples given below. For example, to enable
sampling, in your properties file:

.. sourcecode:: properties

    sample.rate=0.75

Configuration via the runtime environment
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This is the most flexible method for configuring the Sentry client
because it can be easily changed based on the environment you run your
application in.

Two methods are available for runtime configuration, checked in this order: Java System Properties
and System Environment Variables.

Java System Property option names are exactly like the examples given below except that they are
prefixed with ``sentry.``. For example, to enable sampling:

.. sourcecode:: shell

    java -Dsentry.sample.rate=0.75 -jar app.jar

System Environment Variable option names require that you replace the ``.`` with ``_``, capitalize
them, and add a ``SENTRY_`` prefix. For example, to enable sampling:

.. sourcecode:: shell

    SENTRY_SAMPLE_RATE=0.75 java -jar app.jar

Configuration via code
~~~~~~~~~~~~~~~~~~~~~~

The DSN itself can also be configured directly in code:

.. sourcecode:: java

    import io.sentry.Sentry;

    Sentry.init("https://public:private@host:port/1?option=value&other.option=othervalue");

Note that Sentry will not be able to do anything with events until this line is run, so this
method of configuration is not recommended if you might have errors occur during startup.
In addition, by passing a hardcoded DSN you are no longer able to override the DSN at runtime
via Java System Properties or System Environment Variables.

Configuration via the DSN
~~~~~~~~~~~~~~~~~~~~~~~~~

The SDK can also be configured by setting querystring parameters on the DSN itself. This is a bit
recursive because your DSN itself is an option that you must set somewhere (and not in the DSN!).

Option names in the DSN exactly match the examples given below. For example, to enable sampling
if you are setting your DSN via the environment:

.. sourcecode:: shell

    SENTRY_DSN=https://public:private@host:port/1?sample.rate=0.75 java -jar app.jar

You can, of course, pass this DSN in using the other methods described above.

Options
-------

The following options can all be configured as described above: via a ``sentry.properties`` file, via
Java System Properties, via System Environment variables, or via the DSN.

Release
~~~~~~~

To set the application version that will be sent with each event, use the
``release`` option:

::

    release=1.0.0

Distribution
````````````

To set the application distribution that will be sent with each event, use the
``dist`` option:

::

    release=1.0.0
    dist=x86

Note that the distribution is only useful (and used) if the ``release`` is also
set.

Environment
~~~~~~~~~~~

To set the application environment that will be sent with each event, use the
``environment`` option:

::

    environment=staging

Server Name
~~~~~~~~~~~

To set the server name that will be sent with each event, use the
``servername`` option:

::

    servername=host1

Tags
~~~~

To set tags that will be sent with each event, use the ``tags`` option with
comma separated pairs of keys and values that are joined by a colon:

::

    tags=tag1:value1,tag2:value2

MDC Tags
~~~~~~~~

To set tag names that are extracted from the SLF4J MDC system, use the
``mdctags`` option with comma separated key names. Note that this option
is only useful when are you using one of the logging integrations.

::

    mdctags=foo,bar

.. sourcecode:: java

    import org.slf4j.MDC;

    MDC.put("foo", "value1");
    MDC.put("bar", "value2");

    // This sends an event where the 'foo' and 'bar' MDC values are set as additional tags
    logger.error("This is a test");


Extra Data
~~~~~~~~~~

To set extra data that will be sent with each event (but not as tags), use the
``extra`` option with comma separated pairs of keys and values that are joined
by a colon:

::

    extra=key1:value1,key2:value2


"In Application" Stack Frames
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Sentry differentiates stack frames that are directly related to your application
("in application") from stack frames that come from other packages such as the
standard library, frameworks, or other dependencies. The difference
is visible in the Sentry web interface where only the "in application" frames are
displayed by default.

You can configure which package prefixes your application uses with the
``stacktrace.app.packages`` option, which takes a comma separated list.

::

    stacktrace.app.packages=com.mycompany,com.other.name

If you don't want to use this feature but want to disable the warning, simply
set it to an empty string:

::

    stacktrace.app.packages=

Same Frame as Enclosing Exception
`````````````````````````````````

Sentry can use the "in application" system to hide frames in chained exceptions. Usually when a
StackTrace is printed, the result looks like this:

::

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

Some frames are replaced by the ``... N more`` line as they are the same frames
as in the enclosing exception.

Similar behaviour is enabled by default in Sentry. To disable it, use the
``stacktrace.hidecommon`` option.

::

    stacktrace.hidecommon=false

Uncaught Exception Handler
~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, an ``UncaughtExceptionHandler`` is configured that will send exceptions
to Sentry. To disable it, use the ``uncaught.handler.enabled`` option.

::

    uncaught.handler.enabled=false

Event Sampling
~~~~~~~~~~~~~~

Sentry can be configured to sample events with the sample.rate`` option:

::

    sample.rate=0.75

This option takes a number from 0.0 to 1.0, representing the percent of
events to allow through to server (from 0% to 100%). By default all
events will be sent to the Sentry server.

Buffering Events to Disk
~~~~~~~~~~~~~~~~~~~~~~~~

Sentry can be configured to write events to a specified directory on disk
anytime communication with the Sentry server fails with the buffer.dir``
option. If the directory doesn't exist, Sentry will attempt to create it
on startup and may therefore need write permission on the parent directory.
Sentry always requires write permission on the buffer directory itself.

::

    buffer.dir=sentry-events

The maximum number of events that will be stored on disk defaults to 50,
but can also be configured with the option buffer.size``:

::

    buffer.size=100

If a buffer directory is provided, a background thread will periodically
attempt to re-send the events that are found on disk. By default it will
attempt to send events every 60 seconds. You can change this with the
buffer.flushtime`` option (in milliseconds):

::

    buffer.flushtime=10000

Graceful Shutdown of Buffering (Advanced)
`````````````````````````````````````````

In order to shutdown the buffer flushing thread gracefully, a ``ShutdownHook``
is created. By default, the buffer flushing thread is given 1 second
to shutdown gracefully, but this can be adjusted via
buffer.shutdowntimeout`` (represented in milliseconds):

::

    buffer.shutdowntimeout=5000

The special value ``-1`` can be used to disable the timeout and wait
indefinitely for the executor to terminate.

The ``ShutdownHook`` could lead to memory leaks in an environment where
the life cycle of Sentry doesn't match the life cycle of the JVM.

An example would be in a JEE environment where the application using Sentry
could be deployed and undeployed regularly.

To avoid this behaviour, it is possible to disable the graceful shutdown
by setting the buffer.gracefulshutdown`` option:

::

    buffer.gracefulshutdown=false

Async Connection
~~~~~~~~~~~~~~~~

In order to avoid performance issues due to a large amount of logs being
generated or a slow connection to the Sentry server, an asynchronous connection
is set up, using a low priority thread pool to submit events to Sentry.

To disable the async mode, add async=false`` to your options:

::

    async=false

Graceful Shutdown of Async (Advanced)
`````````````````````````````````````

In order to shutdown the asynchronous connection gracefully, a ``ShutdownHook``
is created. By default, the asynchronous connection is given 1 second
to shutdown gracefully, but this can be adjusted via
async.shutdowntimeout`` (represented in milliseconds):

::

    async.shutdowntimeout=5000

The special value ``-1`` can be used to disable the timeout and wait
indefinitely for the executor to terminate.

The ``ShutdownHook`` could lead to memory leaks in an environment where
the life cycle of Sentry doesn't match the life cycle of the JVM.

An example would be in a JEE environment where the application using Sentry
could be deployed and undeployed regularly.

To avoid this behaviour, it is possible to disable the graceful shutdown.
This might lead to some log entries being lost if the log application
doesn't shut down the ``SentryClient`` instance nicely.

The option to do so is async.gracefulshutdown``:

::

    async.gracefulshutdown=false

Async Queue Size (Advanced)
```````````````````````````

The default queue used to store unprocessed events is limited to 50
items. Additional items added once the queue is full are dropped and
never sent to the Sentry server.
Depending on the environment (if the memory is sparse) it is important to be
able to control the size of that queue to avoid memory issues.

It is possible to set a maximum with the option async.queuesize``:

::

    async.queuesize=100

This means that if the connection to the Sentry server is down, only the 100
most recent events will be stored and processed as soon as the server is back up.

The special value ``-1`` can be used to enable an unlimited queue. Beware
that network connectivity or Sentry server issues could mean your process
will run out of memory.

Async Threads Count (Advanced)
``````````````````````````````

By default the thread pool used by the async connection contains one thread per
processor available to the JVM.

It's possible to manually set the number of threads (for example if you want
only one thread) with the option async.threads``:

::

    async.threads=1

Async Threads Priority (Advanced)
`````````````````````````````````

In most cases sending logs to Sentry isn't as important as an application
running smoothly, so the threads have a
`minimal priority <http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY>`_.

It is possible to customise this value to increase the priority of those threads
with the option async.priority``:

::

    async.priority=10

Compression
~~~~~~~~~~~

By default the content sent to Sentry is compressed before being sent.
However, compressing and encoding the data adds a small CPU and memory hit which
might not be useful if the connection to Sentry is fast and reliable.

Depending on the limitations of the project (e.g. a mobile application with a
limited connection, Sentry hosted on an external network), it can be useful
to compress the data beforehand or not.

It's possible to manually enable/disable the compression with the option
compression``

::

    compression=false

Max Message Size
~~~~~~~~~~~~~~~~

By default only the first 1000 characters of a message will be sent to
the server. This can be changed with the maxmessagelength`` option.

::

    maxmessagelength=1500

Timeout (Advanced)
~~~~~~~~~~~~~~~~~~

A timeout is set to avoid blocking Sentry threads because establishing a
connection is taking too long.

It's possible to manually set the timeout length with timeout``
(in milliseconds):

::

    timeout=10000

Using a Proxy
~~~~~~~~~~~~~

If your application needs to send outbound requests through an HTTP proxy,
you can configure the proxy information via JVM networking properties or
as a Sentry option.

For example, using JVM networking properties (affects the entire JVM process),

::

    java \
      # if you are using the HTTP protocol \
      -Dhttp.proxyHost=proxy.example.com \
      -Dhttp.proxyPort=8080 \
      \
      # if you are using the HTTPS protocol \
      -Dhttps.proxyHost=proxy.example.com \
      -Dhttps.proxyPort=8080 \
      \
      # relevant to both HTTP and HTTPS
      -Dhttp.nonProxyHosts=”localhost|host.example.com” \
      \
      MyApp

See `Java Networking and
Proxies <http://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html>`_
for more information about the proxy properties.

Alternatively, using Sentry options (only affects the Sentry HTTP client,
useful inside shared application containers),

::

    http.proxy.host=proxy.example.com
    http.proxy.port=8080

Custom functionality
--------------------

At times, you may require custom functionality that is not included in the Java SDK
already. The most common way to do this is to create your own ``SentryClientFactory`` instance
as seen in the example below.

Implementation
~~~~~~~~~~~~~~

.. sourcecode:: java

    public class MySentryClientFactory extends DefaultSentryClientFactory {
        @Override
        public SentryClient createSentryClient(Dsn dsn) {
            SentryClient sentryClient = new SentryClient(createConnection(dsn), getContextManager(dsn));

            /*
            Create and use the ForwardedAddressResolver, which will use the
            X-FORWARDED-FOR header for the remote address if it exists.
             */
            ForwardedAddressResolver forwardedAddressResolver = new ForwardedAddressResolver();
            sentryClient.addBuilderHelper(new HttpEventBuilderHelper(forwardedAddressResolver));

            sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));
            return configureSentryClient(sentryClient, dsn);
        }
    }

Usage
~~~~~

To use your custom ``SentryClientFactory`` implementation, use the ``factory`` option:

::

    factory=my.company.SentryClientFactory

Your factory class will need to be available on your classpath with a zero argument constructor
or an error will be thrown.
