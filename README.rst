Raven-java
==========
Raven-java is a Java client for Sentry. It is a basic log4j appender that will send your log messages to a sentry server of your choice.

This is a very raw project at the moment it still needs some more TLC and testing before I would consider it production ready.

Installation
------------
Copy the raven-java-0.2.jar file to your java classpath and then configure log4j to use the SentryAppender.

The raven-java-0.2.jar is a self contained jar file, all dependencies are included, so this jar should be all you need.

There is an example project checked into github.com where you can see an example log4j config file.

You will need to add the SentryAppender and the sentry_dsn properties.

sentry_dsn
~~~~~~~~~~
You will get this value from the "Projects / default / Manage / Member: <username>" page. It will be under "Client DSN".
Don't put quotes around it, because it will mess it up, just put it like you see it below.

Log4j Config example::

    log4j.appender.sentry=net.kencochrane.sentry.SentryAppender
    log4j.appender.sentry.sentry_dsn=http://b4935bdd78624092ac2bc70fdcdb6f5a:7a37d9ad4765428180316bfec91a27ef@localhost:8000/1


Sentry Versions Supported
-------------------------
This client has been tested with Sentry 2.7 and 2.8, and only very briefly.

TODO
----
- Add a ant task to build the jar files (I made this first one from intellij (10.5 community edition) File->  Project structure -> artifacts.
- Make this maven friendly. Not familiar with Maven, so maybe someone else can help with this.
- Create better documentation
- Add unit tests
- Add more examples
- Add more documentation
- Get compression to work on message body, it isn't working now,not sure if it is sentry server or raven-java. Might be incompatible versions of zlib Java->python.



History
-------
0.2 - code refactor and cleanup
0.1 - initial version