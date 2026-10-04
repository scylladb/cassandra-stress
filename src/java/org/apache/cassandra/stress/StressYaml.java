// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StressYaml
{
    public String specname;
    public String keyspace;
    public String keyspace_definition;
    public String table;
    public String table_definition;

    public List<String> extra_definitions;

    public List<Map<String, Object>> columnspec;
    public Map<String, QueryDef> queries;
    public Map<String, String> insert;
    public Map<String, TokenRangeQueryDef> token_range_queries = new HashMap<>();

    public static class QueryDef
    {
        public String cql;
        public String fields;
        public String consistencyLevel;
        public String serialConsistencyLevel;
        public String getConfigAsString()
        {
            String output = String.format("CQL:%s;Fields:%s;", cql, fields);
            if (consistencyLevel != null)
                output += String.format("consistencyLevel:%s;", consistencyLevel);
            if (serialConsistencyLevel != null)
                output += String.format("serialConsistencyLevel:%s;", serialConsistencyLevel);
            return output;
        }
    }

    public static class TokenRangeQueryDef
    {
        public String columns;
        public int page_size = 5000;
        public String getConfigAsString()
        {
            return String.format("Columns:%s;", columns);
        }
    }

}
