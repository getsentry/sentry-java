# AppEngine (module)
Module enabling the support of the async connections in Google App Engine.

Google App Engine doesn't support threads but provides instead a TaskQueueing system allowing tasks to be run in the
background.

This module replaces the async system provided by default with one relying on the tasks.

__This module is not useful outside of Google App Engine.__

## Installation

### Maven
```xml
<dependency>
    <groupId>net.kencochrane.raven</groupId>
    <artifactId>raven-appengine</artifactId>
    <version>5.0.1</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Cnet.kencochrane.raven%7Craven%7C4.1.2%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)

## Usage

This module provides a new `RavenFactory` which replaces the default async system with a GAE compatible one.
By default, the default task queue will be used, but it's possible to specify which one will be used with the
`raven.async.gaequeuename` option:

    http://public:private@getsentry.com/1?raven.async.gaequeuename=MyQueueName

The queue size and thread options will not be used as they are specific to the default multithreaded system.

It is necessary to force the raven factory name to `net.kencochrane.raven.appengine.AppEngineRavenFactory`.
