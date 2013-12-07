# GetSentry (module)
Module enabling the support of the GetSentry.com SSL certificate.

GetSentry.com SSL certificate is provided by StartCom which isn't included in the list of the default CAs in
Oracle JDK 6 and 7.

To work around that, this module embeds the certificate and uses it only for HTTPS connections to GetSentry.com

__This module is not useful with Open JDK.__

## Installation

### Maven
```xml
<dependency>
    <groupId>net.kencochrane.raven</groupId>
    <artifactId>raven-getsentry</artifactId>
    <version>4.1.2</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Cnet.kencochrane.raven%7Craven%7C4.1.2%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)

## Usage

This module provides a new `RavenFactory` which supports the use of the 'getsentry://' scheme.
To use it, the DSN should look like this:

    getsentry://public:private@getsentry.com/1

The DSN supports the same options as those listed in the main README file.

## Android

As mentioned in the main README file, Android might require some additional configuration to use a custom `RavenFactory`.

With the GetSentry module, the factory to register is `net.kencochrane.raven.getsentry.GetSentryRavenFactory`.

Both factories can be registered at the same time without any problem.
