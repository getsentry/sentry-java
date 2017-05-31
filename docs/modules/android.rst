Android
=======

Features
--------

The Sentry Android SDK is built on top of the main Java SDK and supports all of the same
features, `configuration options <https://docs.sentry.io/clients/java/config/>`_, and more.
Adding version ``1.0.0`` of the Android SDK to a sample application that doesn't even use
Proguard only increased the release ``.apk`` size by approximately 200KB.

Events will be `buffered to disk <https://docs.sentry.io/clients/java/config/#buffering-events-to-disk>`_
(in the application's cache directory) by default. This allows events to be sent at a
later time if the device does not have connectivity when an event is created. This can
be disabled by setting the DSN option ``sentry.buffer.enabled`` to ``false``.

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

    compile 'io.sentry:sentry-android:1.0.0'

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-android%7C1.0.0%7Cjar>`_.

Initialization
--------------

Your application must have permission to access the internet in order to send
events to the Sentry server. In your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

Then initialize the Sentry client in your application's main ``onCreate`` method:

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.android.AndroidSentryClientFactory;

    public class MainActivity extends Activity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Use the Sentry DSN (client key) from the Project Settings page on Sentry
            String sentryDsn = "https://publicKey:secretKey@host:port/1?options";
            Context ctx = this.getApplicationContext();
            Sentry.init(sentryDsn, new AndroidSentryClientFactory(ctx));
        }
    }

=======
You can also configure your Sentry DSN (client key) in your ``AndroidManifest.xml``:

.. sourcecode:: xml

    <application>
      <meta-data
        android:name="io.sentry.android.DSN"
        android:value="https://publicKey:secretKey@host:port/1?options" />
    </application>

And then you don't need to manually provide the DSN in your code:

.. sourcecode:: java

    import io.sentry.Sentry;
    import io.sentry.android.AndroidSentryClientFactory;

    public class MainActivity extends Activity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Context ctx = this.getApplicationContext();
            Sentry.init(new AndroidSentryClientFactory(ctx));
        }
    }

Usage
-----

Now you can use ``Sentry`` to capture events anywhere in your application:

.. sourcecode:: java

    import io.sentry.context.Context;
    import io.sentry.event.BreadcrumbBuilder;
    import io.sentry.event.UserBuilder;

    public class MyClass {
        /**
         * An example method that throws an exception.
         */
        void unsafeMethod() {
            throw new UnsupportedOperationException("You shouldn't call this!");
        }

        /**
         * Note that the ``Sentry.init`` method must be called before the static API
         * is used, otherwise a ``NullPointerException`` will be thrown.
         */
        void logWithStaticAPI() {
            /*
            Record a breadcrumb in the current context which will be sent
            with the next event(s). By default the last 100 breadcrumbs are kept.
            */
            Sentry.record(new BreadcrumbBuilder().setMessage("User made an action").build());

            // Set the user in the current context.
            Sentry.setUser(new UserBuilder().setEmail("hello@sentry.io").build());

            /*
            This sends a simple event to Sentry using the statically stored instance
            that was created in the ``main`` method.
            */
            Sentry.capture("This is a test");

            try {
                unsafeMethod();
            } catch (Exception e) {
                // This sends an exception event to Sentry using the statically stored instance
                // that was created in the ``main`` method.
                Sentry.capture(e);
            }
        }
    }

