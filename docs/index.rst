.. sentry:edition:: self

    Sentry Java
    ===========

.. sentry:edition:: on-premise, hosted

    .. class:: platform-java

    Java
    ====

Sentry for Java is the official Java SDK for Sentry. At its core it provides
a raw client for sending events to Sentry, but it is highly recommended that you
use one of the included library or framework integrations listed below if at all possible.

**Note:** The old ``raven`` library is no longer maintained. It is highly recommended that
you `migrate <https://docs.sentry.io/clients/java/migration/>`_ to ``sentry`` (which this
documentation covers). `Check out the migration guide <https://docs.sentry.io/clients/java/migration/>`_
for more information. If you are still using ``raven`` you can
`find the old documentation here <https://github.com/getsentry/sentry-java/blob/raven-java-8.x/docs/modules/raven.rst>`_.

.. toctree::
    :maxdepth: 2
    :titlesonly:

    config
    context
    usage
    agent
    migration
    modules/index

Resources:

* `Documentation <https://docs.sentry.io/clients/java/>`_
* `Examples <https://github.com/getsentry/examples>`_
* `Bug Tracker <http://github.com/getsentry/sentry-java/issues>`_
* `Code <http://github.com/getsentry/sentry-java>`_
* `Mailing List <https://groups.google.com/group/getsentry>`_
* `IRC <irc://irc.freenode.net/sentry>`_  (irc.freenode.net, #sentry)
* `Travis CI <http://travis-ci.org/getsentry/sentry-java>`_
