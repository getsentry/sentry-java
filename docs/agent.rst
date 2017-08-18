Agent (Beta)
============

As of version 1.5.0 there is a new **experimental (beta)** Java Agent available that
enhances the existing Sentry Java SDK. The agent will enhance your application stacktraces
on Sentry by adding the names and values of local variables to each frame.

Usage
-----

The latest agent can be `downloaded from Github <https://github.com/getsentry/sentry-java/releases>`_.

Once you have downloaded the correct agent, you need to run your Java application with
the ``-agentpath`` argument. For example:

.. sourcecode:: shell

    java -agentpath:/path/to/libsentry_agent_linux-x86_64.so -jar app.jar

You will still need to install and configure the `Sentry Java SDK <https://docs.sentry.io/clients/java/>`_.
In addition, **you must set the** ``stacktrace.app.packages`` option. Only exceptions that contain at
least one frame from your application will be processed by the agent. You can find details about this option
`on the configuration page <https://docs.sentry.io/clients/java/config/#in-application-stack-frames>`_.

With the SDK configured the agent should automatically enhance your events where applicable.
