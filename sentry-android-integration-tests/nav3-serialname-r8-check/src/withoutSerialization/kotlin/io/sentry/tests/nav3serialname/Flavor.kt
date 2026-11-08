package io.sentry.tests.nav3serialname

const val FLAVOR = "withoutSerialization"

fun flavorExpectations(): List<Expectation> = emptyList()

fun flavorMinificationChecks(): List<MinificationCheck> =
  listOf(
    MinificationCheck(
      "NotSerializable was renamed (runtime class ${NotSerializable::class.java.name})",
      NotSerializable::class.java.name != "io.sentry.tests.nav3serialname.NotSerializable",
    )
  )
