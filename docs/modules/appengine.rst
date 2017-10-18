Google App Engine
=================

The ``sentry-appengine`` library provides `Google App Engine <https://cloud.google.com/appengine/>`_
support for Sentry via the `Task Queue API
<https://cloud.google.com/appengine/docs/java/taskqueue/>`_.

The source can be found `on Github
<https://github.com/getsentry/sentry-java/tree/master/sentry-appengine>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>io.sentry</groupId>
        <artifactId>sentry-appengine</artifactId>
        <version>1.6.0</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'io.sentry:sentry-appengine:1.6.0'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "io.sentry" % "sentry-appengine" % "1.6.0"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-appengine%7C1.6.0%7Cjar>`_.

Usage
-----

This module provides a new ``SentryClientFactory`` implementation which replaces the default async
system with a Google App Engine compatible one. You'll need to configure Sentry to use the
``io.sentry.appengine.AppEngineSentryClientFactory`` as its factory.

The queue size and thread options will not be used as they are specific to
the default Java threading system.

Queue Name
----------

By default, the default task queue will be used, but it's possible to
specify which one will be used with the ``sentry.async.gae.queuename`` option::

    http://public:private@host:port/1?async.gae.queuename=MyQueueName

Connection Name
---------------

As the queued tasks are sent across different instances of the
application, it's important to be able to identify which connection should
be used when processing the event. To do so, the GAE module will identify
each connection based on an identifier either automatically generated or
user defined. To manually set the connection identifier (only used
internally) use the option ``sentry.async.gae.connectionid``::

    http://public:private@host:port/1?async.gae.connectionid=MyConnection
