package com.scylladb.stress.generate.values;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */


import com.scylladb.serializers.TypeSerializer;
import com.scylladb.stress.generate.Distribution;
import com.scylladb.stress.generate.DistributionFactory;
import com.scylladb.stress.settings.OptionDistribution;

public abstract class Generator<T> {
    public final String name;
    public final Type<T> type;
    public final Class<?> clazz;
    final long salt;
    final Distribution identityDistribution;
    final Distribution sizeDistribution;
    public final Distribution clusteringDistribution;

    public Generator(TypeSerializer<T> type, GeneratorConfig config, String name, Class<T> clazz) {
        this(type, config, ComparisonType.BYTE_ORDER, name, clazz);
    }

    public Generator(TypeSerializer<T> type, GeneratorConfig config, ComparisonType comparisonType, String name, Class<T> clazz) {
        this.type = switch (this) {
            case CustomComparer customComparer -> new Type<>(type, comparisonType, customComparer);
            default -> new Type<>(type, comparisonType, null);
        };

        this.name = name;
        this.clazz = clazz;
        this.salt = config.salt;
        this.identityDistribution = config.getIdentityDistribution(defaultIdentityDistribution());
        this.sizeDistribution = config.getSizeDistribution(defaultSizeDistribution());
        this.clusteringDistribution = config.getClusteringDistribution(defaultClusteringDistribution());
    }

    public void setSeed(long seed) {
        identityDistribution.setSeed(seed ^ salt);
        clusteringDistribution.setSeed(seed ^ ~salt);
    }

    public abstract T generate();

    DistributionFactory defaultIdentityDistribution() {
        return OptionDistribution.get("uniform(1..100B)");
    }

    DistributionFactory defaultSizeDistribution() {
        return OptionDistribution.get("uniform(4..8)");
    }

    DistributionFactory defaultClusteringDistribution() {
        return OptionDistribution.get("fixed(1)");
    }
}
