#!/usr/bin/env bash
#export MAVEN_OPTS='-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4000' 
if hash rlwrap &>/dev/null; then
  RLWRAP=rlwrap
fi
exec $RLWRAP mvn -f "$(dirname "$0")/pom.xml" -q exec:java -Dexec.mainClass=org.bdgp.OpenHiCAMM.SqlTool -Dexec.classpathScope=compile -Dexec.args="$*"
