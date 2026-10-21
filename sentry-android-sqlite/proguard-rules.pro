##---------------Begin: proguard configuration for SQLite  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

# SentrySQLiteDriver.create() uses a runtime class-name check to skip wrapping the Room 2.7+
# SupportSQLiteDriver bridge adapter and avoid duplicate spans.
-keepnames class androidx.sqlite.driver.SupportSQLiteDriver

##---------------End: proguard configuration for SQLite  ----------
