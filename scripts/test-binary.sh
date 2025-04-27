#!/bin/bash

set -xe

JAVA=$1
OS=$2
VERSION=$3
TYPE=$4
WORKING_DIR=$5

SCRIPT=""

if [ "$TYPE" == "deb" ]; then
    SCRIPT="install-deb.sh"
elif [ "$TYPE" == "rpm" ]; then
    SCRIPT="install-rpm.sh"
else
    echo "Unsupported distribution type: $TYPE"
    exit 1
fi

docker run --rm -v /tmp:/tmp -v "$WORKING_DIR:/mnt" -w /mnt "$OS" "./scripts/$SCRIPT" "$JAVA" "$VERSION"
