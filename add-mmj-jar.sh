#!/usr/bin/env bash 
test -z "$1" && echo "Usage: $0 micromanager-srcdir" >&2 && exit 1
mvn install:install-file -Dfile="$1"/mmstudio/MMJ_.jar -DgroupId=MicroManager -DartifactId=MMJ_ -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
mvn install:install-file -Dfile="$1"/MMCoreJ_wrap/MMCoreJ.jar -DgroupId=MicroManager -DartifactId=MMCoreJ -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
mvn install:install-file -Dfile="$1"/plugins/PixelCalibrator.jar -DgroupId=MicroManager -DartifactId=PixelCalibrator -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
mvn install:install-file -Dfile="$1"/plugins/DataBrowser.jar -DgroupId=MicroManager -DartifactId=DataBrowser -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
mvn install:install-file -Dfile="$1"/plugins/Big.jar -DgroupId=MicroManager -DartifactId=Big -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
mvn install:install-file -Dfile="$1"/plugins/Recall.jar -DgroupId=MicroManager -DartifactId=Recall -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar
