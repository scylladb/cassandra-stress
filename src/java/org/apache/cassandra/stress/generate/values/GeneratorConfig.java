// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.generate.values;

import java.io.Serializable;
import java.nio.ByteBuffer;

import org.apache.cassandra.stress.generate.Distribution;
import org.apache.cassandra.stress.generate.DistributionFactory;
import org.apache.cassandra.stress.util.ByteBufferUtil;
import org.apache.cassandra.stress.util.MurmurHash;

public class GeneratorConfig implements Serializable
{
    public final long salt;

    private final DistributionFactory clusteringDistributions;
    private final DistributionFactory sizeDistributions;
    private final DistributionFactory identityDistributions;

    public GeneratorConfig(String seedStr, DistributionFactory clusteringDistributions, DistributionFactory sizeDistributions, DistributionFactory identityDistributions)
    {
        this.clusteringDistributions = clusteringDistributions;
        this.sizeDistributions = sizeDistributions;
        this.identityDistributions = identityDistributions;
        ByteBuffer buf = ByteBufferUtil.bytes(seedStr);
        long[] hash = new long[2];
        MurmurHash.hash3_x64_128(buf, buf.position(), buf.remaining(), 0, hash);
        salt = hash[0];
    }

    Distribution getClusteringDistribution(DistributionFactory deflt)
    {
        return (clusteringDistributions == null ? deflt : clusteringDistributions).get();
    }

    Distribution getIdentityDistribution(DistributionFactory deflt)
    {
        return (identityDistributions == null ? deflt : identityDistributions).get();
    }

    Distribution getSizeDistribution(DistributionFactory deflt)
    {
        return (sizeDistributions == null ? deflt : sizeDistributions).get();
    }

    public String getConfigAsString()
    {
        StringBuilder sb = new StringBuilder();
        if (clusteringDistributions != null){
            sb.append(String.format("Clustering: %s;", clusteringDistributions.getConfigAsString()));
        }
        if (sizeDistributions != null){
            sb.append(String.format("Size: %s;", sizeDistributions.getConfigAsString()));
        }
        if (identityDistributions != null){
            sb.append(String.format("Identity: %s;", identityDistributions.getConfigAsString()));
        }
        return sb.toString();
    }
}
