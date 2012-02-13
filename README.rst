Raven-java
==========
Raven-java is a Java client for Sentry. It is a basic log4j appender that will send your log messages to a sentry server of your choice.

This is a very raw project at the moment it still needs some more TLC and testing before I would consider it production ready.

Installation
------------
You'll need Maven 2 to build the project::

    $ cd raven-java
    $ mvn package -Dmaven.test.skip

The last step will build the standalone raven-java jar file but also a jar file containing raven-java and all dependencies, which
you'll find in the target directory of the project.

**Option 1**: add raven-java as a dependency when you're using Maven::

    <dependency>
        <groupId>net.kencochrane</groupId>
        <artifactId>raven-java</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>

**Option 2**: add the plain jar and the jar files of all dependencies to your classpath

**Option 3**: add the self contained jar file to your classpath

Log4J configuration
-------------------
Check out src/test/java/resources/log4j_configuration.txt where you can see an example log4j config file.

You will need to add the SentryAppender and the sentry_dsn properties.

sentry_dsn
~~~~~~~~~~
You will get this value from the "Projects / default / Manage / Member: <username>" page. It will be under "Client DSN".
Don't put quotes around it, because it will mess it up, just put it like you see it below.

Log4j Config example::

    log4j.appender.sentry=net.kencochrane.sentry.SentryAppender
    log4j.appender.sentry.sentry_dsn=http://b4935bdd78624092ac2bc70fdcdb6f5a:7a37d9ad4765428180316bfec91a27ef@localhost:8000/1

Proxy
~~~~~
If you need to use a proxy for HTTP transport, you can configure it as well::

    log4j.appender.sentry.proxy=HTTP:proxyhost:proxyport

Sentry Versions Supported
-------------------------
This client supports Sentry protocol version 2.0 (which is Sentry >= 2.0)

Other
-----
If you want to generate the javadocs for this project, simply run ``mvn javadoc:javadoc`` and you'll be able to browse the
docs from the target directory of the project.

TODO
----
- Create better documentation
- Add unit tests
- Add more examples
- Get compression to work on message body, it isn't working now,not sure if it is sentry server or raven-java. Might be incompatible versions of zlib Java->python.


History
-------
0.2 - code refactor and cleanup

0.1 - initial version

Contributors
------------
Ken Cochrane (@KenCochrane)
Kevin Wetzels (@roambe)