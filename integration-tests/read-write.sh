#!/bin/bash

set -xe

IP=$1
DRIVER_TYPE=${2:-3x}
[[ "$DRIVER_TYPE" == "3x" ]] && DRIVER_TYPE=native

cassandra-stress write cl=QUORUM n=10000 \
    -errors fail-fast \
    -schema 'replication(strategy=NetworkTopologyStrategy,replication_factor=1)' \
    -mode cql3 "${DRIVER_TYPE}" \
    -rate threads=4 \
    -pop seq=1..20971520 \
    -col 'n=FIXED(10) size=FIXED(512)' \
    -log interval=5 \
    -node "$IP" datacenter=datacenter1 \
    || exit 1

cassandra-stress read cl=QUORUM n=10000 \
    -errors fail-fast \
    -schema 'replication(strategy=NetworkTopologyStrategy,replication_factor=1)' \
    -mode cql3 "${DRIVER_TYPE}" \
    -rate threads=4 \
    -pop seq=1..20971520 \
    -col 'n=FIXED(10) size=FIXED(512)' \
    -log interval=5 \
    -node "$IP" datacenter=datacenter1 \
    || exit 1
