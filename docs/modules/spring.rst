Spring
======

The ``sentry-spring`` library provides `Spring <https://spring.io/>`_
support for Sentry via a `HandlerExceptionResolver
<https://docs.spring.io/spring/docs/4.3.9.RELEASE/javadoc-api/org/springframework/web/servlet/HandlerExceptionResolver.html>`_
that sends exceptions to Sentry. Once this integration is configured
you can *also* use Sentry's static API, :ref:`as shown on the usage page <usage_example>`,
in order to do things like record breadcrumbs, set the current user, or manually send
events.

The source can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry-spring>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry-spring</artifactId>
        <version>1.6.4</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry-spring:1.6.4'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry-spring" % "1.6.4"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-spring%7C1.6.4%7Cjar>`_.

Usage
-----

The ``sentry-spring`` library provides two classes that can be enabled by
registering them as Beans in your Spring application.

Recording Exceptions
~~~~~~~~~~~~~~~~~~~~

In order to record all exceptions thrown by your controllers, you can register
``io.sentry.spring.SentryExceptionResolver`` as a Bean in your application. Once
registered, all exceptions will be sent to Sentry and then passed on to the default
exception handlers.

**Note** that you should **not** configure the ``SentryExceptionResolver``
alongside a logging integration (such as ``sentry-logback``), or you will most
likely double-report exceptions. You should use one or the other depending on
your needs. A logging integration is more general and will capture errors (and
possibly warnings, depending on your configuration) that occur inside *or outside*
of a Spring controller.

Configuration via ``web.xml``:

.. sourcecode:: xml

    <bean class="io.sentry.spring.SentryExceptionResolver"/>

Or via a configuration class:

.. sourcecode:: java

    @Bean
    public HandlerExceptionResolver sentryExceptionResolver() {
        return new io.sentry.spring.SentryExceptionResolver();
    }

Next, **you'll need to configure your DSN** (client key) and optionally other values such as
``environment`` and ``release``.   :ref:`See the configuration page <setting_the_dsn>` for ways you can do this.

Spring Boot HTTP Data
~~~~~~~~~~~~~~~~~~~~~

Spring Boot doesn't automatically load any ``javax.servlet.ServletContainerInitializer``,
which means the Sentry SDK doesn't have an opportunity to hook into the request cycle
to collect information about the HTTP request. In order to add HTTP request data to
your Sentry events in Spring Boot, you need to register the
``io.sentry.spring.SentryServletContextInitializer`` class as a Bean in your application.

Configuration via ``web.xml``:

.. sourcecode:: xml

    <bean class="io.sentry.spring.SentryServletContextInitializer"/>

Or via a configuration class:

.. sourcecode:: java

    @Bean
    public ServletContextInitializer sentryServletContextInitializer() {
        return new io.sentry.spring.SentryServletContextInitializer();
    }

After that, your Sentry events should contain information such as HTTP request headers.