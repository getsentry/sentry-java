Google App Engine
=================

The ``raven-appengine`` library provides `Google App Engine <https://cloud.google.com/appengine/>`_
support for Raven via the `Task Queue API
<https://cloud.google.com/appengine/docs/java/taskqueue/>`_.

The source can be found `on Github
<https://github.com/getsentry/raven-java/tree/master/raven-appengine>`_.

Installation
------------

Using Maven:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-appengine</artifactId>
        <version>8.0.3</version>
    </dependency>

Using Gradle:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-appengine:8.0.3'

Using SBT:

.. sourcecode:: scala

    libraryDependencies += "com.getsentry.raven" % "raven-appengine" % "8.0.3"

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-appengine%7C8.0.3%7Cjar>`_.

Usage
-----

This module provides a new ``RavenFactory`` implementation which replaces the default async
system with a Google App Engine compatible one. You'll need to configure Raven to use the
``com.getsentry.raven.appengine.AppEngineRavenFactory`` as its factory.

The queue size and thread options will not be used as they are specific to
the default Java threading system.

Queue Name
----------

By default, the default task queue will be used, but it's possible to
specify which one will be used with the ``raven.async.gae.queuename`` option::

    http://public:private@host:port/1?raven.async.gae.queuename=MyQueueName

Connection Name
---------------

As the queued tasks are sent across different instances of the
application, it's important to be able to identify which connection should
be used when processing the event. To do so, the GAE module will identify
each connection based on an identifier either automatically generated or
user defined. To manually set the connection identifier (only used
internally) use the option ``raven.async.gae.connectionid``::

    http://public:private@host:port/1?raven.async.gae.connectionid=MyConnection
