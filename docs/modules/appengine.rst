Google App Engine
=================

The `raven-appengine` module enables the support of the async connections
in Google App Engine.

The project can be found on github: `raven-java/appengine
<https://github.com/getsentry/raven-java/tree/master/raven-appengine>`_

Google App Engine doesn't support threads but provides instead a
TaskQueueing system allowing tasks to be run in the background.

This module replaces the async system provided by default with one relying
on the tasks.

This module is not useful outside of Google App Engine.

Installation
------------

If you want to use Maven you can install Raven-AppEngine as dependency:

.. sourcecode:: xml

    <dependency>
        <groupId>com.getsentry.raven</groupId>
        <artifactId>raven-appengine</artifactId>
        <version>7.8.1</version>
    </dependency>

Usage
-----

This module provides a new ``RavenFactory`` which replaces the default async
system with a GAE compatible one.

The queue size and thread options will not be used as they are specific to
the default multithreaded system.

It is necessary to force the raven factory name to
``com.getsentry.raven.appengine.AppEngineRavenFactory``.

Queue Name
----------

By default, the default task queue will be used, but it's possible to
specify which one will be used with the ``raven.async.gae.queuename`` option::

    ___DSN___?raven.async.gae.queuename=MyQueueName

Connection Name
---------------

As the queued tasks are sent across different instances of the
application, it's important to be able to identify which connection should
be used when processing the event. To do so, the GAE module will identify
each connection based on an identifier either automatically generated or
user defined. TO manually set the connection identifier (only used
internally) use the option ``raven.async.connectionid``::

    ___DSN___?raven.async.gae.connectionid=MyConnection
