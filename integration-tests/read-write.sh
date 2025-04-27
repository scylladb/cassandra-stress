#!/bin/bash

set -xe

IP=$1

cassandra-stress write cl=QUORUM duration=1m \
    -errors fail-fast \
    -schema 'replication(strategy=NetworkTopologyStrategy,replication_factor=1)' \
    -mode cql3 native \
    -rate threads=4 \
    -pop seq=1..20971520 \
    -col 'n=FIXED(10) size=FIXED(512)' \
    -log interval=5 \
    -node "$IP" || exit 1

cassandra-stress read cl=QUORUM duration=1m \
    -errors fail-fast \
    -schema 'replication(strategy=NetworkTopologyStrategy,replication_factor=1)' \
    -mode cql3 native \
    -rate threads=4 \
    -pop seq=1..20971520 \
    -col 'n=FIXED(10) size=FIXED(512)' \
    -log interval=5 \
    -node "$IP" || exit 1
