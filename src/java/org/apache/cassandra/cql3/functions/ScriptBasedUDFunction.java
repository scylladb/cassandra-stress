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
package org.apache.cassandra.cql3.functions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import javax.script.*;

import org.apache.cassandra.concurrent.NamedThreadFactory;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.security.SecurityThreadGroup;
import org.apache.cassandra.security.ThreadAwareSecurityManager;
import org.apache.cassandra.transport.ProtocolVersion;

final class ScriptBasedUDFunction extends UDFunction
{
    ScriptBasedUDFunction(FunctionName name,
                          List<ColumnIdentifier> argNames,
                          List<AbstractType<?>> argTypes,
                          AbstractType<?> returnType,
                          boolean calledOnNullInput,
                          String language,
                          String body)
    {
        super(name, argNames, argTypes, returnType, calledOnNullInput, language, body);
        throw new RuntimeException("Scripts are not supported");
    }

    protected ExecutorService executor()
    {
        throw new RuntimeException("Scripts are not supported");
    }

    public ByteBuffer executeUserDefined(ProtocolVersion protocolVersion, List<ByteBuffer> parameters)
    {
        throw new RuntimeException("Scripts are not supported");
    }

    /**
     * Like {@link UDFunction#executeUserDefined(ProtocolVersion, List)} but the first parameter is already in non-serialized form.
     * Remaining parameters (2nd paramters and all others) are in {@code parameters}.
     * This is used to prevent superfluous (de)serialization of the state of aggregates.
     * Means: scalar functions of aggregates are called using this variant.
     */
    protected Object executeAggregateUserDefined(ProtocolVersion protocolVersion, Object firstParam, List<ByteBuffer> parameters)
    {
        throw new RuntimeException("Scripts are not supported");
    }

}
