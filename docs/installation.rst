Installation
============

It is recommended that you use one of the supported logging framework integrations
rather than the raw ``raven-java`` client.

- `java.util.logging <http://docs.oracle.com/javase/7/docs/technotes/guides/logging/index.html>`_
  support is provided by ``raven-java``
- `log4j 1.x <https://logging.apache.org/log4j/1.2/>`_ support is provided by ``raven-log4j``
- `log4j 2.x <https://logging.apache.org/log4j/2.x/>`_ support is provided by ``raven-log4j2``
- `logback <http://logback.qos.ch/>`_ support is provided by ``raven-logback``

Support for Google App Engine is provided in ``raven-appengine``.

Source
------

The source for ``raven-java`` can be found `on Github
<https://github.com/getsentry/raven-java/>`_.

Android
-------

Raven works on Android, and relies on the `ServiceLoader
<https://developer.android.com/reference/java/util/ServiceLoader.html>`_
system which uses the content of ``META-INF/services``. This is used to
declare the ``RavenFactory`` implementations (to allow more control over
the automatically generated instances of ``Raven``) in
``META-INF/services/com.getsentry.raven.RavenFactory``.

Unfortunately, when the APK is build, the content of ``META-INF/services`` of
the dependencies is lost, this prevent Raven to work properly. Solutions
exist for that problem:

*   Use `maven-android-plugin
    <https://code.google.com/p/maven-android-plugin/>`_ which has already
    solved `this problem <https://code.google.com/p/maven-android-plugin/issues/detail?id=97>`_
*   Create manually a
    ``META-INF/services/com.getsentry.raven.RavenFactory`` for the
    project which will contain the canonical name of of implementation of
    ``RavenFactory`` (ie. ``com.getsentry.raven.DefaultRavenFactory``).
*   Register manually the ``RavenFactory`` when the application starts:

    .. sourcecode:: java

        RavenFactory.registerFactory(new DefaultRavenFactory());
