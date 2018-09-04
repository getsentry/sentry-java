# Contributing to sentry-java

We love pull requests from everyone.

The test suite currently requires you run JDK version `1.7.0_80`.
See [#487](https://github.com/getsentry/sentry-java/issues/478) 
for more information.

To run the tests (and checkstyle):

```shell
make test
```

Tests are automatically run against branches and pull requests
via TravisCI, so you can also depend on that if you'd rather not
deal with installing an older JDK.
