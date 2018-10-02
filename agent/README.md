# sentry-java-agent

The Sentry Java Agent collects and stores local variable information for each stack frame
when an exception is created. If local variable information is available the Sentry Java
SDK will send the variable information along with events.

Build: `cmake CMakeLists.txt && make`

Run: `java -agentpath:libsentry_agent{.dylib,.so} ...`

![Example of local variable state in the Sentry UI](/agent/example.png)
