Installation
============

When using Sentry with Java the strongly recommended way is to use one of
the supported logging framework integrations rather than the raw
"raven-java" clients.

- `java.util.logging <http://docs.oracle.com/javase/7/docs/technotes/guides/logging/index.html>`_
  support is provided by the main project "raven-java"
- `log4j <https://logging.apache.org/log4j/1.2/>`_ support is provided in raven-log4j
- `log4j2 <https://logging.apache.org/log4j/2.x/>`_ can be used with raven-log4j2
- `logback <http://logback.qos.ch/>`_ support is provided in raven-logback

Support for Google App Engine is provided in raven-appengine.

Github
------

ALl modules are available on the `raven-java github project
<https://github.com/getsentry/raven-java/>`_.   This includes raven-java
itself as well as all the logging integrations.

Snapshot Versions
-----------------

While the stable versions of raven are available on the central Maven
Repository, newer (but less stable) versions (AKA snapshots) are available
in Sonatype's snapshot repository.

To use it with maven, add the following repository:

.. sourcecode:: xml

    <repository>
        <id>sonatype-nexus-snapshots</id>
        <name>Sonatype Nexus Snapshots</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>

Android
-------

Raven works on Android, and relies on the `ServiceLoader
<https://developer.android.com/reference/java/util/ServiceLoader.html>`_
system which uses the content of ``META-INF/services``. This is used to
declare the ``RavenFactory`` implementations (to allow more control over
the automatically generated instances of ``Raven``) in
``META-INF/services/net.kencochrane.raven.RavenFactory``.

Unfortunately, when the APK is build, the content of ``META-INF/services`` of
the dependencies is lost, this prevent Raven to work properly. Solutions
exist for that problem:

*   Use `maven-android-plugin
    <https://code.google.com/p/maven-android-plugin/>`_ which has already
    solved `this problem <https://code.google.com/p/maven-android-plugin/issues/detail?id=97>`_
*   Create manually a
    ``META-INF/services/net.kencochrane.raven.RavenFactory`` for the
    project which will contain the canonical name of of implementation of
    ``RavenFactory`` (ie. ``net.kencochrane.raven.DefaultRavenFactory``).
*   Register manually the ``RavenFactory`` when the application starts:

    .. sourcecode:: java

        RavenFactory.registerFactory(new DefaultRavenFactory());
