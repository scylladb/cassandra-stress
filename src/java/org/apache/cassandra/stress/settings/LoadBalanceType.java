/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.stress.settings;

import java.util.Arrays;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RackAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;

public enum LoadBalanceType
{
    ROUND_ROBIN("round-robin", new LoadBalanceStrategyProvidable() {
        @Override
        public LoadBalancingPolicy createPolicy(StressSettings settings)
        {
            return new RoundRobinPolicy();
        }
    }),
    DC_AWARE("dc-aware", new LoadBalanceStrategyProvidable() {
        @Override
        public LoadBalancingPolicy createPolicy(StressSettings settings)
        {
            DCAwareRoundRobinPolicy.Builder builder = DCAwareRoundRobinPolicy.builder();
            if (settings.node.datacenter != null)
                builder.withLocalDc(settings.node.datacenter);
            return builder.build();
        }
    }),
    RACK_AWARE("rack-aware", new LoadBalanceStrategyProvidable() {
        @Override
        public LoadBalancingPolicy createPolicy(StressSettings settings)
        {
            RackAwareRoundRobinPolicy.Builder builder = RackAwareRoundRobinPolicy.builder();
            if (settings.node.datacenter != null)
                builder.withLocalDc(settings.node.datacenter);
            if (settings.node.rack != null)
                builder.withLocalRack(settings.node.rack);
            return builder.build();
        }
    });

    private final String value;
    private final LoadBalanceStrategyProvidable strategy;

    LoadBalanceType(String value, LoadBalanceStrategyProvidable strategy)
    {
        this.value = value;
        this.strategy = strategy;
    }

    public String getValue()
    {
        return value;
    }

    public LoadBalancingPolicy createPolicy(StressSettings settings)
    {
        return strategy.createPolicy(settings);
    }

    public static LoadBalanceType fromString(String value)
    {
        if (value == null)
            return null;
            
        return Arrays.stream(values())
                .filter(strategy -> strategy.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown load balance strategy: " + value));
    }
}
