// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.nio.ByteBuffer;
import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.marshal.AbstractType;
import org.apache.cassandra.stress.settings.OptionDistribution;

public abstract class Generator<T> {

    public final String name;
    public final AbstractType<T> type;
    public final Class<?> clazz;
    final long salt;
    final Distribution identityDistribution;
    final Distribution sizeDistribution;
    public final Distribution clusteringDistribution;

    public Generator(AbstractType<T> type, GeneratorConfig config, String name, Class<?> clazz) {
        this.type = type;
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

    public Object read(ByteBuffer bytes) {
        return fromStoredValue(type.compose(bytes));
    }

    Object fromStoredValue(Object value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    public int compareStored(Object left, Object right) {
        return type.compare(type.decompose((T) left), type.decompose((T) right));
    }

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
