#!/bin/sh

set -xe

. /etc/os-release

JAVA=$1
VERSION=$2

mkdir -p /etc/yum.repos.d

cat <<EOF >/etc/yum.repos.d/adoptium.repo
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/$ID/\$releasever/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOF

dnf update -y
dnf install -y "temurin-$JAVA-jre" awk || exit 1
dnf install -y ./cassandra-stress-java"$JAVA"-"$VERSION"-1.noarch.rpm || exit 1

cassandra-stress version >>/tmp/version.log || exit 1
