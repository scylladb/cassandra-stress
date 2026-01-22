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

package org.apache.cassandra.stress.settings;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class SettingsNodeTest
{
    @Test
    public void testRemoteDcOptionParsing()
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-node", new String[]{"remote-dc=5"});
        
        SettingsNode settings = SettingsNode.get(clArgs);
        
        assertNotNull("usedHostsPerRemoteDc should not be null", settings.usedHostsPerRemoteDc);
        assertEquals("usedHostsPerRemoteDc should be 5", Integer.valueOf(5), settings.usedHostsPerRemoteDc);
    }
    
    @Test
    public void testRemoteDcOptionNotSet()
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-node", new String[]{});
        
        SettingsNode settings = SettingsNode.get(clArgs);
        
        assertNull("usedHostsPerRemoteDc should be null when not set", settings.usedHostsPerRemoteDc);
    }
    
    @Test
    public void testRemoteDcWithOtherOptions()
    {
        Map<String, String[]> clArgs = new HashMap<>();
        clArgs.put("-node", new String[]{"datacenter=dc1", "remote-dc=3", "localhost"});
        
        SettingsNode settings = SettingsNode.get(clArgs);
        
        assertEquals("datacenter should be dc1", "dc1", settings.datacenter);
        assertNotNull("usedHostsPerRemoteDc should not be null", settings.usedHostsPerRemoteDc);
        assertEquals("usedHostsPerRemoteDc should be 3", Integer.valueOf(3), settings.usedHostsPerRemoteDc);
    }
}
