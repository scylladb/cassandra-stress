#!/bin/bash

set -ex

. /etc/os-release

print_usage() {
    echo "build_deb.sh --version 3.17.0"
    echo "  --version cassandra-stress version"
    exit 1
}

while [ $# -gt 0 ]; do
    case "$1" in
        "--version")
            VERSION="$2"
            shift 2
            ;;
        *)
            print_usage
            ;;
    esac
done

cd build || exit 1
rm -rf "cassandra-stress" "cassandra-stress-$VERSION"
cp -r dist "cassandra-stress-$VERSION"
tar -czf "cassandra-stress_$VERSION.orig.tar.xz" "cassandra-stress-$VERSION"
mkdir -p "cassandra-stress-$VERSION/debian"
mv "cassandra-stress_$VERSION.orig.tar.xz" "cassandra-stress-$VERSION/debian"

cd "cassandra-stress-$VERSION" || exit 1
rm -rf debian
mkdir -p debian
cp -r ../../dist/debian/* ./debian
sed -i "s/%version/$VERSION/g" debian/changelog
dpkg-buildpackage -us -uc
