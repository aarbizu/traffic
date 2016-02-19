#!/bin/bash
#
# wrapper to invoke the maven-build jar as part of a container process
jar_name=$(ls traffic/target/traffic*.jar)

echo "Starting traffic checker..."
echo "running java -jar ${jar_name}"
java -jar ${jar_name}
