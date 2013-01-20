Raven-java
==========
Raven-java is a Java client for Sentry. It is a basic log4j appender that will send your log messages to a sentry server of your choice.

It is officially recognized as production-ready by Sentry: http://sentry.readthedocs.org/en/latest/client/index.html

The log4j appender is asyncronous by design so there is no need to put it in a AsyncAppender.

Current Status
--------------
.. image:: https://secure.travis-ci.org/kencochrane/raven-java.png
   :target: http://travis-ci.org/kencochrane/raven-java


Installation
------------

Download Jars
~~~~~~~~~~~~~
Precompiled jars are available for download directly from github.

    https://github.com/kencochrane/raven-java/downloads

Build Jars yourself
~~~~~~~~~~~~~~~~~~~
You'll need Maven 2 to build the project::

    $ cd raven-java
    $ mvn package -Dmaven.test.skip

The last step will build the standalone raven-java jar file but also a jar file containing raven-java and all dependencies, which
you'll find in the target directory of the project.

**Option 1**: add raven-java as a dependency when you're using Maven::

    <dependency>
        <groupId>net.kencochrane</groupId>
        <artifactId>raven-java</artifactId>
        <version>0.6-SNAPSHOT</version>
    </dependency>

**Option 2**: add the plain jar and the jar files of all dependencies to your classpath

**Option 3**: add the self contained jar file to your classpath

Configuration
-------------

Log4J configuration
~~~~~~~~~~~~~~~~~~~
Check out ``src/test/java/resources/log4j_configuration.txt`` where you can see an example log4j config file.

You will need to add the SentryAppender and the sentry_dsn properties.

sentry_dsn
^^^^^^^^^^
You will get this value from the "Projects / default / Manage / Member: <username>" page. It will be under "Client DSN".
Don't put quotes around it, because it will mess it up, just put it like you see it below.

Log4j Config example::

    log4j.appender.sentry=net.kencochrane.sentry.SentryAppender
    log4j.appender.sentry.sentry_dsn=http://b4935bdd7862409:7a37d9ad47654281@localhost:8000/1

Proxy
^^^^^
If you need to use a proxy for HTTP transport, you can configure it as well::

    log4j.appender.sentry.proxy=HTTP:proxyhost:proxyport

Queue Size
^^^^^^^^^^
By default, the appender is configured with a queue of 1000 events.  To tune this parameter::

    log4j.appender.sentry.queue_size=100

Blocking
^^^^^^^^
By default, the appender is non-blocking.  If the queue is filled then log events will be written to Standard Error.
If you want to make sure log events always reach sentry, you can turn blocking on::

    log4j.appender.sentry.blocking=true

WARNING: By setting blocking true, you will effectively lock up the thread doing the logging! Use with care.

Naive SSL
^^^^^^^^^
If you're using Java 6 or earlier, you might encounter errors when connecting to a Sentry instance using a TLS (https)
connection since Server Name Indication (SNI) support has only been available in Java since Java 7. You can either add
the corresponding certificate to your keystore (recommended!) or enable ``naiveSsl`` on the Sentry appender. This will
make the Raven client use a custom hostname verifier that *will* allow the JVM to connect with the host - in fact it
will let the Raven client connect to any host even if the certificate is invalid. Use at your own risk::

    log4j.appender.sentry.naiveSsl=true


SENTRY_DSN Environment Variable
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Raven-Java will first look to see if there is an environment variable called ``SENTRY_DSN`` set before it looks at the log4j config. If the environment variable is set, it will use that value instead of the one set in ``log4j.appender.sentry.sentry_dsn``. This is for a few reasons.

1. Some hosting providers (Heroku, etc) have http://GetSentry.com plugins and they expose your Sentry settings via an environment variable. 
2. This allows you to specify your Sentry config by server. You could put a development Sentry server in your log4j properties file and then on each server override those values to point to different sentry servers if you need to.
3. It allows you to use the RavenClient directly outside of log4J, without having to hard code the Sentry DSN in your source code.

Linux example::

    # put this in your profile, or add it to a shell script that calls your java program.
    
    $ export SENTRY_DSN=http://b4935bdd78624092a:7a37d9ad47654281803f@localhost:8000/1

Usage
-----

Log4J
~~~~~

If you configure log4j to only error messages to Sentry, it will ignore all other message levels and only send the logger.error() messages

Example::

    // configure log4j the normal way, and then just use it like you normally would.
    
    logger.debug("Debug example"); // ignored
    logger.error("Error example"); // sent to sentry
    logger.info("info Example"); // ignored
    
    try {
        throw new RuntimeException("Uh oh!");
    } catch (RuntimeException e) {
        logger.error("Error example with stacktrace", e); //sent to sentry
    }


RavenClient
~~~~~~~~~~~
Set the SENTRY_DSN Environment Variable with your sentry DSN.

Create an instance of the client::

    RavenClient client = new RavenClient();
    
Now call out to the raven client to capture events::

    // record a simple message
    client.captureMessage("hello world!");

    // capture an exception
    try {
        throw new RuntimeException("Uh oh!");
    }
    catch (Throwable e) {
        client.captureException(e);
    }


Sentry Versions Supported
-------------------------
This client supports Sentry protocol version 2.0 (which is Sentry >= 2.0)

Other
-----
If you want to generate the javadocs for this project, simply run ``mvn javadoc:javadoc`` and you'll be able to browse the
docs from the target directory of the project.

Running Tests
-------------
We are using maven, so all that you need to do in order to run the test is run the following::

    $ cd raven-java
    $ mvn test

TODO
----
- Create better documentation
- Add more unit tests
- Add more examples
- Get compression to work on message body, it isn't working now, not sure if it is sentry server or raven-java. Might be incompatible versions of zlib Java->python.


History
-------
- 0.6
    - Added support for sending messages through UDP
- 0.5
    - Added async support
    - Fixed issue with parsing of path and port in DSN
- 0.4
    - Added the ability to get the SENTRY_DSN from the ENV
    - Added RavenClient.captureMessage
    - Added RavenClient.captureException
- 0.3
    - Added Maven support
    - Merged with log4sentry project by Kevin Wetzels
    - Added Proxy support
    - Added full stack trace to logs

- 0.2
    - code refactor and cleanup

- 0.1
    - initial version

Contributors
------------
- Ken Cochrane (@KenCochrane)
- Kevin Wetzels (@roambe)
- David Cramer (@zeeg)
- Mark Philpot (@griphiam)

License
-------
We are using the same license as the Sentry master project which is a BSD license. For more information see the LICENSE file, or follow this link: http://en.wikipedia.org/wiki/BSD_licenses
