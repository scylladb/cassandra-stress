#!/bin/bash

set -xe

IP=$1

cassandra-stress user "ops(insert=1)" "profile=$PWD/examples/cqlstress-counter-example.yaml" no-warmup n=10000 cl=QUORUM \
    -errors fail-fast \
    -node "$IP" \
    -rate threads=4 || exit 1
