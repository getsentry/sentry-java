# Raven-Android

## Installation

### Gradle (Android Studio)

In your `app/build.gradle` add: `compile 'com.getsentry.raven:raven-android:7.8.1'`

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-android%7C7.8.1%7Cjar).

## Usage

### Configuration

Configure your Sentry DSN (client key) in `AndroidManifest.xml`:

```xml
<application>
  <meta-data
    android:name="com.getsentry.raven.android.DSN"
    android:value="https://publicKey:secretKey@host:port/1?options" />
</application>
```

Your application must also have permission to access the internet in order to send
event to the Sentry server. In `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Then, in your application's `onCreate`, initialize the Raven client:

```java
import com.getsentry.raven.android.Raven;

// `this` is your main Activity
Raven.init(this.getApplicationContext());
```

Now you can use `Raven` to capture events in your application:

```java
// Pass a String event 
Raven.capture("Error message");

// Or pass it a throwable
try {
  something()
} catch (Exception e) {
  Raven.capture(e);
}

// Or build an event yourself
EventBuilder eventBuilder = new EventBuilder()
                              .withMessage("Exception caught")
                              .withLevel(Event.Level.ERROR);
Raven.capture(eventBuilder.build());
```
