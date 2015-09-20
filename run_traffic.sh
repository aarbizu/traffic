#!/bin/bash
#
# wrapper to invoke the maven-build jar as part of a container process
jar_name=$(ls traffic/target/traffic*.jar)

print "Starting traffic checker..."
java -jar $jar_name
