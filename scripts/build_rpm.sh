#!/bin/bash

set -xe

. /etc/os-release

print_usage() {
    echo "build_rpm.sh --version 3.17.0"
    echo "  --version cassandra-stress version"
    echo "  --builddir specify rpmbuild directory"
    exit 1
}

RELOC_PKG=build/cassandra-stress-bin.tar.gz
BUILDDIR=build/redhat
while [ $# -gt 0 ]; do
    case "$1" in
    "--builddir")
        BUILDDIR="$2"
        shift 2
        ;;
    "--version")
        VERSION="$2"
        shift 2
        ;;
    *)
        print_usage
        ;;
    esac
done

RELOC_PKG=$(readlink -f "$RELOC_PKG")
RPMBUILD=$(readlink -f "$BUILDDIR")
mkdir -p "$BUILDDIR"
tar -C "$BUILDDIR" -xpf "$RELOC_PKG"

mkdir -p $RPMBUILD/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

ln -fv "$RELOC_PKG" "$RPMBUILD/SOURCES/cassandra-stress.tar.gz"

cp dist/redhat/cassandra-stress.spec "$RPMBUILD/SPECS"
# this rpm can be install on both fedora / centos7, so drop distribution name from the file name
rpmbuild -ba \
    --define "version $VERSION" \
    --define "_topdir $RPMBUILD" \
    --undefine "dist" \
    "$RPMBUILD/SPECS/cassandra-stress.spec"

mv "build/redhat/RPMS/noarch/cassandra-stress-$VERSION-1.noarch.rpm" "build/cassandra-stress-$VERSION-1.noarch.rpm"
