/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scylladb.stress.settings;

import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RackAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;

public enum LoadBalanceType {
    ROUND_ROBIN(settings -> new RoundRobinPolicy()),
    DC_AWARE(settings -> {
        DCAwareRoundRobinPolicy.Builder builder = DCAwareRoundRobinPolicy.builder();
        if (settings.node().datacenter != null)
            builder.withLocalDc(settings.node().datacenter);
        return builder.build();
    }),
    RACK_AWARE(settings -> {
        RackAwareRoundRobinPolicy.Builder builder = RackAwareRoundRobinPolicy.builder();
        if (settings.node().datacenter != null)
            builder.withLocalDc(settings.node().datacenter);
        if (settings.node().rack != null)
            builder.withLocalRack(settings.node().rack);
        return builder.build();
    });

    private final LoadBalanceStrategyProvidable strategy;

    LoadBalanceType(LoadBalanceStrategyProvidable strategy) {
        this.strategy = strategy;
    }

    public LoadBalancingPolicy createPolicy(StressSettings settings) {
        return strategy.createPolicy(settings);
    }

    public static LoadBalanceType fromString(String value) {
        return switch (value == null ? null : value.toLowerCase()) {
            case null -> null;
            case "rr", "roundrobin", "round-robin" -> ROUND_ROBIN;
            case "dc", "dc-aware" -> DC_AWARE;
            case "rack", "rack-aware" -> RACK_AWARE;
            default -> throw new IllegalArgumentException("Unknown load balance strategy: " + value);
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case ROUND_ROBIN -> "round-robin";
            case DC_AWARE -> "dc-aware";
            case RACK_AWARE -> "rack-aware";
            default -> throw new IllegalArgumentException();
        };
    }
}
