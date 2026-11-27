# Migrating from 8.x to 9.x

## Replace `sendDefaultPii` with Data Collection

Version 9 removes `sendDefaultPii` from the Java API, external configuration, Android manifest metadata, and Spring's `@EnableSentry` annotation. Configure each category through `dataCollection` instead.

When no Data Collection setting is specified, the SDK now uses these defaults:

- User information, database query data, file paths, GraphQL documents and variables, and HTTP request and response bodies are enabled.
- Cookies, URL query parameters, and HTTP request and response headers are collected with their default deny lists.

Explicit settings still take precedence. If you relied on `sendDefaultPii=false` and want to disable these categories, configure them directly:

```java
Sentry.init(options -> {
  DataCollection dataCollection = options.getDataCollection();
  dataCollection.setUserInfo(false);
  dataCollection.setDatabaseQueryData(false);
  dataCollection.setFilePaths(false);
  dataCollection.setCookies(KeyValueCollectionBehavior.off());
  dataCollection.setUrlQueryParams(KeyValueCollectionBehavior.off());
  dataCollection.setHttpBodies(java.util.Collections.emptySet());
  dataCollection.getHttpHeaders().setRequest(KeyValueCollectionBehavior.off());
  dataCollection.getHttpHeaders().setResponse(KeyValueCollectionBehavior.off());
  dataCollection.getGraphql().setDocument(false);
  dataCollection.getGraphql().setVariables(false);
});
```

`DataCollection.forceDataCollection()` is also removed because the documented defaults now apply automatically.

For Logback, use `includeUnencodedMessage` to control whether events include the original message template and parameters when an encoder is configured. OpenTelemetry-derived HTTP headers now follow `dataCollection.httpHeaders.request`.

# Migrating from `sentry-java` to `sentry-android`

#### Docs

Migration page from [sentry-android 1.x to sentry-android 2.x](https://docs.sentry.io/platforms/android/migrate).

#### Installation

_Old_:

```
Sentry.init("___PUBLIC_DSN___", new AndroidSentryClientFactory(context));
```

_New_:

The SDK is able to initialize automatically. The only thing required is to make the DSN available.
*`sentry.properties` has been discontinued and configurations on this SDK version is over `AndroidManifest.xml` or code.*

```xml
<meta-data android:name="io.sentry.dsn" android:value="___PUBLIC_DSN___" />
```
_Or:_

*If you want to call `SentryAndroid.init(...)` by yourself, first of all you need to disable the `auto-init` feature.*

```xml
<meta-data android:name="io.sentry.auto-init" android:value="false" />
```

```
SentryAndroid.init(context, options -> {
  options.setDsn("___PUBLIC_DSN___");    
});
```

#### Set tag

_Old_:

```
Sentry.getContext().addTag("tagName", "tagValue");
```

_New_:

```
Sentry.setTag("tagName", "tagValue");
```

#### Capture custom exception

_Old_:

```
try {
  int x = 1 / 0;
} catch (Exception e) {
  Sentry.capture(e);
}
```

_New_:

```
try {
  int x = 1 / 0;
} catch (Exception e) {
  Sentry.captureException(e);
}
```

#### Capture a custom event

_Old_:

```
Exception exception = new Exception("custom error");
EventBuilder eventBuilder = new EventBuilder()
  .withLevel(Event.Level.ERROR)
  .withSentryInterface(new ExceptionInterface(exception));
Sentry.capture(eventBuilder);
```

_New_:

```
Exception exception = new Exception("custom error");
SentryEvent event = new SentryEvent(exception);
event.setLevel(SentryLevel.ERROR);
Sentry.captureEvent(event);
```

#### Capture a message

_Old_:

```
Sentry.capture("This is a test");
```

_New_:

```
Sentry.captureMessage("This is a test"); // SentryLevel.INFO by default
Sentry.captureMessage("This is a test", SentryLevel.WARNING); // or specific level
```

#### Breadcrumbs

_Old_:

```
Sentry.getContext().recordBreadcrumb(
  new BreadcrumbBuilder().setMessage("User made an action").build()
);
```

_New_:

```
Sentry.addBreadcrumb("User made an action");
```

#### User

_Old_:

```
Sentry.getContext().setUser(
  new UserBuilder().setEmail("hello@sentry.io").build()
);
```

_New_:

```
User user = new User();
user.setEmail("hello@sentry.io");
Sentry.setUser(user);
```

#### Set extra

_Old_:

```
Sentry.getContext().addExtra("extra", "thing");
```

_New_:

```
Sentry.setExtra("extra", "thing");
```
