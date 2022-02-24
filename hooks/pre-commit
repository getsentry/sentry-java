#!/bin/sh
# From gist at https://gist.github.com/chadmaughan/5889802

echo '[git hook] executing gradle spotless check before commit'

# run the spotlessCheck with the gradle wrapper
make checkFormat

# store the last exit code in a variable
RESULT=$?

# return the './gradlew spotlessCheck' exit code
exit $RESULT
