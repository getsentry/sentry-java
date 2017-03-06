Android
=======

Installation
------------

Using Gradle (Android Studio) in your ``app/build.gradle`` add:

.. sourcecode:: groovy

    compile 'com.getsentry.raven:raven-android:7.8.3'

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-android%7C7.8.3%7Cjar>`_.

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
