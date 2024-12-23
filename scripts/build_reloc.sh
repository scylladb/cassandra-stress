#!/bin/bash -e

# shellcheck disable=SC1091
. /etc/os-release

print_usage() {
    echo "build_reloc.sh --clean "
    echo "  --clean clean build directory"
    echo "  --dest DEST specify relocatable package path"
    echo "  --version V "
    exit 1
}

is_redhat_variant() {
    [ -f /etc/redhat-release ]
}

is_debian_variant() {
    [ -f /etc/debian_version ]
}

CLEAN=
VERSION=
DEST="build/cassandra-stress-$VERSION.noarch.tar.gz"

while [ $# -gt 0 ]; do
    case "$1" in
        "--clean")
            CLEAN=yes
            shift 1
            ;;
        "--version")
            VERSION="$2"
            shift 2
            ;;
        "--dest")
            DEST="$2"
            shift 2
            ;;
        *)
            print_usage
            ;;
    esac
done


if [ ! -e reloc/build_reloc.sh ]; then
    echo "run build_reloc.sh in top of scylla dir"
    exit 1
fi

if [ "$CLEAN" = "yes" ]; then
    rm -rf build target
fi

if [ -f "$DEST" ]; then
    rm "$DEST"
fi

printf "version=%s" "$VERSION" > build.properties

ant artifacts || exit 1

scripts/create-relocatable-package.py --version "$VERSION" "$DEST"

tar -zvf "$DEST" \
    --transform='s|^build/lib/jars|lib|' \
    --transform="s|^build/apache-cassandra-$VERSION.jar|apache-cassandra-$VERSION.jar|" \
    --transform="s|^build/apache-cassandra-$VERSION.jar|apache-cassandra-$VERSION.jar|" \
    conf/ \
    bin/ \
    build/lib/jars \
    "build/lib/jars/apache-cassandra-$VERSION.jar" \
    "build/lib/jars/apache-cassandra-thrift-$VERSION.jar" \
    README.md \
    CHANGELOG.md \
    LICENSE.txt \
    NEWS.txt \
    NOTICE.txt \
