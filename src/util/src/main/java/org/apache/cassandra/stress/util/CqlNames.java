package org.apache.cassandra.stress.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CqlNames {
    private static final String NAME = "(\"(?:[^\"]|\"\")+\"|\\w+)";
    private static final String LEADING_COMMENTS = "^(?:\\s+|--[^\\n]*|//[^\\n]*|/\\*(?s:.*?)\\*/)*";
    private static final String IF_NOT_EXISTS = "(?:IF\\s+NOT\\s+EXISTS\\s+)?";

    private static final Pattern CREATE_KEYSPACE = Pattern.compile(
            LEADING_COMMENTS + "CREATE\\s+KEYSPACE\\s+" + IF_NOT_EXISTS + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_TABLE = Pattern.compile(
            LEADING_COMMENTS + "CREATE\\s+(?:TABLE|COLUMNFAMILY)\\s+" + IF_NOT_EXISTS + "(?:" + NAME + "\\s*\\.\\s*)?"
                    + NAME,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UNQUOTED = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> RESERVED = Set.of(
            "add",
            "allow",
            "alter",
            "and",
            "apply",
            "asc",
            "authorize",
            "batch",
            "begin",
            "by",
            "columnfamily",
            "create",
            "default",
            "delete",
            "desc",
            "describe",
            "drop",
            "entries",
            "execute",
            "from",
            "full",
            "grant",
            "if",
            "in",
            "index",
            "infinity",
            "insert",
            "into",
            "is",
            "keyspace",
            "limit",
            "materialized",
            "mbean",
            "mbeans",
            "modify",
            "nan",
            "norecursive",
            "not",
            "null",
            "of",
            "on",
            "or",
            "order",
            "primary",
            "rename",
            "replace",
            "revoke",
            "schema",
            "select",
            "set",
            "table",
            "to",
            "token",
            "truncate",
            "unlogged",
            "unset",
            "update",
            "use",
            "using",
            "view",
            "where",
            "with");

    private CqlNames() {}

    public static String quote(String identifier) {
        return UNQUOTED.matcher(identifier).matches() && !RESERVED.contains(identifier)
                ? identifier
                : '"' + identifier.replace("\"", "\"\"") + '"';
    }

    public static String keyspaceOf(String createKeyspaceCql) {
        Matcher matcher = CREATE_KEYSPACE.matcher(createKeyspaceCql);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a CREATE KEYSPACE statement: " + createKeyspaceCql);
        }
        return unquote(matcher.group(1));
    }

    public static String tableOf(String createTableCql) {
        Matcher matcher = CREATE_TABLE.matcher(createTableCql);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a CREATE TABLE statement: " + createTableCql);
        }
        return unquote(matcher.group(2));
    }

    private static String unquote(String name) {
        if (name.startsWith("\"")) {
            return name.substring(1, name.length() - 1).replace("\"\"", "\"");
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
