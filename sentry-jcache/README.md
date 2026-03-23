# sentry-jcache

This module provides an integration for JCache (JSR-107).

JCache is a standard API — you need a provider implementation at runtime. Common implementations include:

- [Caffeine](https://github.com/ben-manes/caffeine) (via `com.github.ben-manes.caffeine:jcache`)
- [Ehcache 3](https://www.ehcache.org/) (via `org.ehcache:ehcache`)
- [Hazelcast](https://hazelcast.com/)
- [Apache Ignite](https://ignite.apache.org/)
- [Infinispan](https://infinispan.org/)

Please consult the documentation on how to install and use this integration in the Sentry Docs for [Java](https://docs.sentry.io/platforms/java/tracing/instrumentation/jcache/).
