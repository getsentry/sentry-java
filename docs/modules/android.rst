Android
=======

Features
--------

The Raven Android SDK is built on top of the main Java SDK and supports all of the same
features, `configuration options <https://docs.sentry.io/clients/java/config/>`_, and more.
Adding version ``8.0.0`` of the Android SDK to a sample application that doesn't even use
Proguard only increased the release ``.apk`` size by approximately 200KB.

Events will be `buffered to disk <https://docs.sentry.io/clients/java/config/#buffering-events-to-disk>`_
(in the application's cache directory) by default. This allows events to be sent at a
later time if the device does not have connectivity when an event is created. This can
be disabled by setting the DSN option ``raven.buffer.enabled`` to ``false``.

An ``UncaughtExceptionHandler`` is configured so that crash events will be
stored to disk and sent the next time the application is run.

Installation
------------

Using Gradle (Android Studio) in your ``app/build.gradle`` add:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-android:7.8.5'

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-android%7C7.8.5%7Cjar>`_.

Usage
-----

Configure your Sentry DSN (client key) in your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <application>
      <meta-data
        android:name="com.getsentry.raven.android.DSN"
        android:value="https://publicKey:secretKey@host:port/1?options" />
    </application>

Your application must also have permission to access the internet in order to send
event to the Sentry server. In your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

Then, in your application's ``onCreate``, initialize the Raven client:

.. sourcecode:: java

    import com.getsentry.raven.android.Raven;

    // "this" should be a reference to your main Activity
    Raven.init(this.getApplicationContext());

Now you can use ``Raven`` to capture events anywhere in your application:

.. sourcecode:: java

    // Send a simple event to the Sentry server
    Raven.capture("Error message");

    try {
        something()
    } catch (Exception e) {
        // Send an exception event to the Sentry server
        Raven.capture(e);
    }

    // Or build an event manually
    EventBuilder eventBuilder = new EventBuilder()
                                  .withMessage("Exception caught")
                                  .withLevel(Event.Level.ERROR);
    Raven.capture(eventBuilder.build());
