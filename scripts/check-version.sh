#!/bin/bash

set -xe

FILE="${1:-"/tmp/version.log"}"

if [ ! -f "$FILE" ]; then
    echo "Version file not found"
    exit 1
fi

if ! grep -q "Version: 1.0.0" "$FILE"; then
    echo "Version not found in the file"
    exit 1
fi

if ! grep -q "scylla-java-driver: [0-9]\+\.[0-9]\+\.[0-9]\+\.[0-9]\+" "$FILE"; then
    echo "Version not found in the file"
    exit 1
fi
