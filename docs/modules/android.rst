Android
=======

Features
--------

The Raven Android SDK is built on top of the main Java SDK and supports all of the same
features, `configuration options <https://docs.sentry.io/clients/java/config/>`_, and more.
Adding version ``8.0.3`` of the Android SDK to a sample application that doesn't even use
Proguard only increased the release ``.apk`` size by approximately 200KB.

Events will be `buffered to disk <https://docs.sentry.io/clients/java/config/#buffering-events-to-disk>`_
(in the application's cache directory) by default. This allows events to be sent at a
later time if the device does not have connectivity when an event is created. This can
be disabled by setting the DSN option ``raven.buffer.enabled`` to ``false``.

An ``UncaughtExceptionHandler`` is configured so that crash events will be
stored to disk and sent the next time the application is run.

The ``AndroidEventBuilderHelper`` is enabled by default, which will automatically
enrich events with data about the current state of the device, such as memory usage,
storage usage, display resolution, connectivity, battery level, model, Android version,
whether the device is rooted or not, etc.

Installation
------------

Using Gradle (Android Studio) in your ``app/build.gradle`` add:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-android:8.0.3'

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-android%7C8.0.3%7Cjar>`_.

Usage
-----

Your application must have permission to access the internet in order to send
events to the Sentry server. In your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

Then initialize the Raven client in your application's main ``onCreate`` method:

.. sourcecode:: java

    import com.getsentry.raven.android.Raven;

    public class MainActivity extends Activity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Context ctx = this.getApplicationContext();
            // Use the Sentry DSN (client key) from the Project Settings page on Sentry
            String sentryDsn = "https://publicKey:secretKey@host:port/1?options";

            Raven.init(ctx, sentryDsn);
        }
    }

You can also configure your Sentry DSN (client key) in your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <application>
      <meta-data
        android:name="com.getsentry.raven.android.DSN"
        android:value="https://publicKey:secretKey@host:port/1?options" />
    </application>

Now you can use ``Raven`` to capture events anywhere in your application:

.. sourcecode:: java

    // Send a simple event to the Sentry server
    Raven.capture("Error message");

    // Set a breadcrumb that will be sent with the next event(s)
    Breadcrumbs.record(
        new BreadcrumbBuilder().setMessage("User made an action").build()
    );

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
