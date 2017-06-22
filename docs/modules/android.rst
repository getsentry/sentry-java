Android
=======

Features
--------

The Sentry Android SDK is built on top of the main Java SDK and supports all of the same
features, `configuration options <https://docs.sentry.io/clients/java/config/>`_, and more.
Adding version ``1.1.0`` of the Android SDK to a sample application that doesn't even use
Proguard only increased the release ``.apk`` size by approximately 200KB.

Events will be `buffered to disk <https://docs.sentry.io/clients/java/config/#buffering-events-to-disk>`_
(in the application's cache directory) by default. This allows events to be sent at a
later time if the device does not have connectivity when an event is created. This can
be disabled by setting the DSN option ``buffer.enabled`` to ``false``.

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

    compile 'io.sentry:sentry-android:1.1.0'

For other dependency managers see the `central Maven repository <https://search.maven.org/#artifactdetails%7Cio.sentry%7Csentry-android%7C1.1.0%7Cjar>`_.

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

ProGuard
--------

If you want to use ProGuard with Sentry Android you will need to upload
proguard mapping files to Sentry with :doc:`sentry-cli
</learn/cli/proguard>` or by using our Gradle integration.

Gradle Integration
``````````````````

Using Gradle (Android Studio) in your ``app/build.gradle`` add:

.. sourcecode:: groovy

    apply plugin: 'io.sentry.android.gradle'

And declare a dependency in your toplevel ``build.gradle``:

.. sourcecode:: groovy

    buildscript {
        dependencies {
            classpath 'io.sentry:sentry-android-gradle-plugin:1.0.0'
        }
    }

This will then automatically generate appropriate ProGuard mapping files
and upload them when you run ``gradlew assembleRelease``.  The credentials
for the upload step are loaded from the ``sentry.properties`` file in
your project root.  At the very minimum you will need something like this
in there::

    defaults.project=___PROJECT_NAME___
    defaults.org=___ORG_NAME___
    auth.token=YOUR_AUTH_TOKEN

The auth token you can find at `sentry.io/api <https://sentry.io/api/>`.
For more information about the available config keys see
:doc:`/learn/cli/configuration`.

Gradle Configuration
````````````````````

Additionally we expose a few configuration values directly in Gradle:

.. sourcecode:: groovy

    sentry {
        // disables or enables the automatic configuration of proguard
        // for sentry.  This injects a default config for proguard so
        // you don't need to do it manually.
        autoProguardConfig true

        // this enables or disables the automatic upload of mapping files
        // during the build.  If you do not want to do that you can
        // disable that here and later use sentry-cli to manually upload
        // it.
        autoUpload true
    }

Proguard Requirements
`````````````````````

If you want to manually configure ProGuard it's not much more complex.
You just need to follow some minium requirements in your ProGuard rules
file::

    -keepattributes LineNumberTable,SourceFile
    -dontwarn org.slf4j.**
    -dontwarn javax.**

ProGuard UUIDs
``````````````

After ProGuard files are generated you will need to embed the UUIDs of the
ProGuard mapping file in a file named ``sentry-debug-meta.properties`` in
the assets folder.  Sentry-Java will look for the UUIDs there to map them
against the correct mapping.

.. admonition:: Note

    Sentry calculates UUIDs for proguard files.  For more information
    about how this works see :ref:`proguard-uuids`.

``sentry-cli`` can do that for you::

    sentry-cli upload-proguard \
        --android-manifest app/build/intermediates/manifests/full/release/AndroidManifest.xml \
        --write-properties app/build/intermediates/assets/release/sentry-debug-meta.properties \
        --no-upload \
        app/build/outputs/mapping/release/mapping.txt

Note that this will need to end up in your APK so it needs to run before
the APK is packaged.  You can do that by creating a gradle task that runs
before the dex packaging.  However it's *strongly* recommended to use the
gradle plugin which will do that for you.

You can for example add a gradle task after the proguard step and before
the dex one which executes ``sentry-cli`` to just validate and process
the mapping files and to write the UUIDs into the properties file:

.. sourcecode:: groovy

    gradle.projectsEvaluated {
        android.applicationVariants.each { variant ->
            def variantName = variant.name.capitalize();
            def proguardTask = project.tasks.findByName(
                "transformClassesAndResourcesWithProguardFor${variantName}")
            def dexTask = project.tasks.findByName(
                "transformClassesWithDexFor${variantName}")
            def task = project.tasks.create(
                name: "processSentryProguardFor${variantName}",
                type: Exec) {
                workingDir project.rootDir
                commandLine *[
                    "sentry-cli",
                    "upload-proguard",
                    "--write-properties",
                    "${project.rootDir.toPath()}/app/build/intermediates/assets" +
                        "/${variant.dirName}/sentry-debug-meta.properties",
                    variant.getMappingFile(),
                    "--no-upload"
                ]
            }
            dexTask.dependsOn task
            task.dependsOn proguardTask
        }
    }

Alternatively you can generate a UUID upfront yourself and then force
Sentry to honor that UUID after upload.  However this is strongly
discouraged!

Uploading ProGuard Files
````````````````````````

You can then manually upload ProGuard files with ``sentry-cli`` as
follows::

    sentry-cli upload-proguard \
        --android-manifest app/build/intermediates/manifests/full/release/AndroidManifest.xml \
        app/build/outputs/mapping/release/mapping.txt
