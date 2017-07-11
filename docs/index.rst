.. sentry:edition:: self

    Sentry Java
    ===========

.. sentry:edition:: on-premise, hosted

    .. class:: platform-java

    Java
    ====

Sentry for Java (``sentry-java``) is the official Java SDK for Sentry. At its core it provides
a raw client for sending events to Sentry, but it is highly recommended that you
use one of the included library or framework integrations listed below if at all possible.

**Note:** The old ``raven-java`` library is no longer maintained. It is highly recommended that
you migrate to ``sentry-java`` (which this documentation covers). If you are still
using ``raven-java`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/tree/raven-java-8.x/docs>`_.

.. toctree::
    :maxdepth: 2
    :titlesonly:

    config
    context
    usage
    modules/index

Resources:

* `Documentation <https://docs.sentry.io/clients/java/>`_
* `Bug Tracker <http://github.com/getsentry/sentry-java/issues>`_
* `Code <http://github.com/getsentry/sentry-java>`_
* `Mailing List <https://groups.google.com/group/getsentry>`_
* `IRC <irc://irc.freenode.net/sentry>`_  (irc.freenode.net, #sentry)
* `Travis CI <http://travis-ci.org/getsentry/sentry-java>`_
