# QATOOLS-413 — A single slow coordinator fails the request at the client timeout

## Problem

cassandra-stress marks no statement as idempotent and configures no
speculative execution policy. The Java driver applies speculative execution
and retry on the next host only to idempotent statements. So each request
stays on the coordinator that the load balancing policy picks first.

When that coordinator is alive but overloaded, the request waits for the full
client request timeout (12000 ms by default) and then fails with
`OperationTimedOutException`. This occurs at CL=QUORUM with RF=3 and all
replicas up, when another coordinator could complete the request.

The `-errors retries=` option does not help. It retries after the operation
has already failed, and each retry enters the same load balancing plan again.

The Jira issue names `JavaDriverClient.java` in `scylla-tools-java`. In this
repository the same gap is in both driver clients:
`src/java/org/apache/cassandra/stress/driver/v3/JavaDriverV3Client.java` and
`src/java/org/apache/cassandra/stress/driver/v4/JavaDriverV4Client.java`.
Neither sets default idempotence or a speculative execution policy.

## Who it affects

SCT longevity tests that run cassandra-stress against a cluster under load,
and the engineers who triage their failures. A stress failure from one hot
coordinator looks like a cluster availability problem.

## Evidence

Error from the SCT run:

```
com.datastax.driver.core.exceptions.OperationTimedOutException:
  [/10.4.8.102:9042] Timed out waiting for server response
  [configured timeout: 12000ms, connection in-flight: 1,
   pool pending borrows: 0, pool total in-flight: 1]
```

- Jenkins: `scylla-master/tier1/longevity-mv-si-4days-streaming-test #109`
- Argus run: `bdf874cc-f209-4a36-a7d3-7e7e525bb8b1`
- SCT ticket: SCT-571
- Driver issue, closed as driver behavior by design:
  https://github.com/scylladb/java-driver/issues/938
- Server-side overload, a separate bug:
  https://github.com/scylladb/scylladb/issues/27068

From the issue: "The driver deliberately refuses to replay a possibly
non-idempotent statement after a client-side timeout, since the server-side
outcome is unknown."

## What good looks like

- A user can opt in to idempotent statements and speculative execution with a
  documented CLI option, for workloads that are not counter and not LWT.
- With speculative execution on, one slowed coordinator at CL=QUORUM and RF=3
  no longer produces `OperationTimedOutException`.
- Counter workloads and LWT (conditional) statements are never idempotent,
  even when the user passes the option.
- The startup output shows the effective idempotence and speculative execution
  settings.
- Without the option, behavior is the same as today.

## Out of scope

- The server-side overload in scylladb/scylladb#27068.
- Changes to the `-errors retries=` loop in `SettingsErrors`.
- A change of the default behavior for existing runs.
