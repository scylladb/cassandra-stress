#!/bin/bash

set -xe

IP=$1
DRIVER_TYPE=${2:-3x}
[[ "$DRIVER_TYPE" == "3x" ]] && DRIVER_TYPE=native

cassandra-stress user "ops(insert=1)" "profile=$PWD/examples/cqlstress-counter-example.yaml" no-warmup n=10000 cl=QUORUM \
    -errors fail-fast \
    -node "$IP" datacenter=datacenter1 \
    -mode cql3 "${DRIVER_TYPE}" \
    -rate threads=4 || exit 1
