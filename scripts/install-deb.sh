#!/bin/sh

set -xe

JAVA=$1
VERSION=$2

apt-get update

apt install -y wget apt-transport-https gpg

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg >/dev/null

echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

apt-get update && apt-get install "temurin-$JAVA-jre" -y

apt-get install -y ./cassandra-stress-java"$JAVA"_"$VERSION"_all.deb

cassandra-stress version >>/tmp/version.log
