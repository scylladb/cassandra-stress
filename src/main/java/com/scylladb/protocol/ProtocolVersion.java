/*
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

package com.scylladb.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;

/**
 * The native (CQL binary) protocol version.
 *
 * Some versions may be in beta, which means that the client must
 * specify the beta flag in the frame for the version to be considered valid.
 * Beta versions must have the word "beta" in their description, this is mandated
 * by the specs.
 *
 */
public enum ProtocolVersion implements Comparable<ProtocolVersion>
{
    // The order is important as it defines the chronological history of versions, which is used
    // to determine if a feature is supported or some serdes formats
    V1(1, "v1", false), // no longer supported
    V2(2, "v2", false), // no longer supported
    V3(3, "v3", false),
    V4(4, "v4", false),
    V5(5, "v5-beta", true);

    /** The version number */
    private final int num;

    /** A description of the version, beta versions should have the word "-beta" */
    private final String description;

    /** Set this to true for beta versions */
    private final boolean beta;

    ProtocolVersion(int num, String description, boolean beta)
    {
        this.num = num;
        this.description = description;
        this.beta = beta;
    }

    /** The supported versions stored as an array, these should be private and are required for fast decoding*/
    private final static ProtocolVersion[] SUPPORTED_VERSIONS = new ProtocolVersion[] { V3, V4, V5 };
    final static ProtocolVersion MIN_SUPPORTED_VERSION = SUPPORTED_VERSIONS[0];
    final static ProtocolVersion MAX_SUPPORTED_VERSION = SUPPORTED_VERSIONS[SUPPORTED_VERSIONS.length - 1];
    /** These versions are sent by some clients, but are not valid Apache Cassandra versions (66, and 65 are DSE versions) */
    private static int[] KNOWN_INVALID_VERSIONS = { 66, 65};

    /** All supported versions, published as an enumset */
    public final static EnumSet<ProtocolVersion> SUPPORTED = EnumSet.copyOf(Arrays.asList((ProtocolVersion[]) ArrayUtils.addAll(SUPPORTED_VERSIONS)));

    /** Old unsupported versions, this is OK as long as we never add newer unsupported versions */
    public final static EnumSet<ProtocolVersion> UNSUPPORTED = EnumSet.complementOf(SUPPORTED);

    /** The preferred versions */
    public final static ProtocolVersion CURRENT = V4;
    public final static Optional<ProtocolVersion> BETA = Optional.of(V5);

    public static List<String> supportedVersions()
    {
        List<String> ret = new ArrayList<>(SUPPORTED.size());
        for (ProtocolVersion version : SUPPORTED)
            ret.add(version.toString());
        return ret;
    }

    @Override
    public String toString()
    {
        // This format is mandated by the protocl specs for the SUPPORTED message, see OptionsMessage execute().
        return String.format("%d/%s", num, description);
    }
}
