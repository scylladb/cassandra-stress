#!/bin/bash

set -xe

# Usage: ./user-profile-multi-yaml.sh <NODE_IP> [<DRIVER_TYPE>]
# This integration test exercises the multi-YAML user profile feature by
# running cassandra-stress with two profiles at once, addressing each
# profile's operations via the fully-qualified spec name (keyspace.table).

IP=$1
DRIVER_TYPE=${2:-3x}
[[ "$DRIVER_TYPE" == "3x" ]] && DRIVER_TYPE=native

cassandra-stress user \
  "ops(alpha_workload.insert=1,beta_workload.insert=1)" \
  "profile=$PWD/examples/cqlstress-example-specA.yaml,$PWD/examples/cqlstress-counter-example-specB.yaml" \
  no-warmup n=10000 cl=QUORUM \
  -mode cql3 "${DRIVER_TYPE}" \
  -node "$IP" datacenter=datacenter1 \
  -errors fail-fast \
  -rate threads=4 || exit 1
