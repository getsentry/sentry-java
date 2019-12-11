# Migrating from `sentry-java` to `sentry-android`

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
