.. sentry:edition:: self

    Raven Java
    ==========

.. sentry:edition:: on-premise, hosted

    .. class:: platform-java

    Java
    ====

Raven is the Java client for Sentry. Raven relies on the most popular
logging libraries to capture and convert logs before sending details to a
Sentry instance.

While it's strongly recommended to use one of the supported logging
frameworks to capture and send messages to Sentry, a it is possible to do
so manually with the main project raven.

It is recommended that you use one of the supported logging framework integrations
rather than the raw ``raven-java`` client.

- `java.util.logging <http://docs.oracle.com/javase/7/docs/technotes/guides/logging/index.html>`_
  support is provided by ``raven-java``
- `log4j 1.x <https://logging.apache.org/log4j/1.2/>`_ support is provided by ``raven-log4j``
- `log4j 2.x <https://logging.apache.org/log4j/2.x/>`_ support is provided by ``raven-log4j2``
- `logback <http://logback.qos.ch/>`_ support is provided by ``raven-logback``

Support for Google App Engine is provided in ``raven-appengine``.


.. toctree::
    :maxdepth: 2
    :titlesonly:

    config
    usage
    modules/index

Resources:

* `Documentation <https://docs.sentry.io/clients/java/>`_
* `Bug Tracker <http://github.com/getsentry/raven-java/issues>`_
* `Code <http://github.com/getsentry/raven-java>`_
* `Mailing List <https://groups.google.com/group/getsentry>`_
* `IRC <irc://irc.freenode.net/sentry>`_  (irc.freenode.net, #sentry)
* `Travis CI <http://travis-ci.org/getsentry/raven-java>`_