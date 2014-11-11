#!/usr/bin/env bash 
test -z "$1" && echo "Usage: $0 micromanager-dir" >&2 && exit 1
mvn install:install-file -Dfile="$1"/mmstudio/MMJ_.jar -DgroupId=MicroManager -DartifactId=MMJ_ -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar -DlocalRepositoryPath="$PWD"/repository
mvn install:install-file -Dfile="$1"/MMCoreJ_wrap/MMCoreJ.jar -DgroupId=MicroManager -DartifactId=MMCoreJ -Dversion="$(cat "$1"/version.txt)" -Dpackaging=jar -DlocalRepositoryPath="$PWD"/repository
