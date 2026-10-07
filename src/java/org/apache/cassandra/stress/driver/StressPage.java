// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

public record StressPage(StressResult result, Object pagingState, boolean fullyFetched) {}
