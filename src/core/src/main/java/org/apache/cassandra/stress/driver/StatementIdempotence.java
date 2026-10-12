// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.driver;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StatementIdempotence {
    private static final Pattern DML =
            Pattern.compile("^\\s*(SELECT|INSERT|UPDATE|DELETE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION = Pattern.compile("\\bIF\\b", Pattern.CASE_INSENSITIVE);
    private static final String NAME = "(?<![A-Za-z0-9_\"])(\"[^\"]*+(?:\"\"[^\"]*+)*+\"|[A-Za-z_][A-Za-z0-9_]*+)";
    private static final Pattern APPEND = Pattern.compile(NAME + "\\s*=\\s*\\1\\s*[-+]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREPEND =
            Pattern.compile(NAME + "\\s*=[^=+]*+\\+\\s*\\1(?![A-Za-z0-9_\"])", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORTHAND = Pattern.compile("[-+]=");
    private static final Pattern NON_DETERMINISTIC = Pattern.compile(
            "\\b(now|uuid|currentTimeUUID|currentTimestamp|currentDate|currentTime)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_WRITE =
            Pattern.compile("\\bJSON\\s*\\?|\\bfromJson\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern TTL =
            Pattern.compile("\\bUSING\\b.*\\bTTL\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*+");

    private StatementIdempotence() {}

    public static boolean isIdempotent(String cql) {
        return isIdempotent(cql, false);
    }

    public static boolean isIdempotent(String cql, boolean bindsList) {
        String shape = withoutLiterals(cql);
        return DML.matcher(shape).find()
                && !CONDITION.matcher(shape).find()
                && !APPEND.matcher(shape).find()
                && !PREPEND.matcher(shape).find()
                && !SHORTHAND.matcher(shape).find()
                && !NON_DETERMINISTIC.matcher(shape).find()
                && !JSON_WRITE.matcher(shape).find()
                && !deletesAnElement(shape)
                && !(writes(shape)
                        && (bindsList
                                || shape.indexOf('[') >= 0
                                || TTL.matcher(shape).find()));
    }

    private static boolean writes(String shape) {
        String upper = shape.toUpperCase(Locale.ROOT).stripLeading();
        return upper.startsWith("INSERT") || upper.startsWith("UPDATE");
    }

    private static boolean deletesAnElement(String shape) {
        String upper = shape.toUpperCase(Locale.ROOT).stripLeading();
        if (!upper.startsWith("DELETE")) {
            return false;
        }
        int from = upper.indexOf(" FROM ");
        return upper.substring(0, from < 0 ? upper.length() : from).indexOf('[') >= 0;
    }

    static String withoutLiterals(String cql) {
        StringBuilder shape = new StringBuilder(Math.min(cql.length(), 1024));
        int i = 0;
        while (i < cql.length()) {
            char c = cql.charAt(i);
            if (c == '\'') {
                i = skipQuoted(cql, i + 1, '\'');
                shape.append('?');
            } else if (c == '"') {
                int end = skipQuoted(cql, i + 1, '"');
                String name = cql.substring(i + 1, Math.max(i + 1, end - 1));
                shape.append(PLAIN_NAME.matcher(name).matches() ? name : cql.substring(i, end));
                i = end;
            } else if (cql.startsWith("$$", i)) {
                int end = cql.indexOf("$$", i + 2);
                i = end < 0 ? cql.length() : end + 2;
                shape.append('?');
            } else if (cql.startsWith("--", i) || cql.startsWith("//", i)) {
                int end = cql.indexOf('\n', i);
                i = end < 0 ? cql.length() : end;
                shape.append(' ');
            } else if (cql.startsWith("/*", i)) {
                int end = cql.indexOf("*/", i + 2);
                i = end < 0 ? cql.length() : end + 2;
                shape.append(' ');
            } else if (c == '0'
                    && i + 1 < cql.length()
                    && (cql.charAt(i + 1) == 'x' || cql.charAt(i + 1) == 'X')
                    && (i == 0 || !Character.isLetterOrDigit(cql.charAt(i - 1)))) {
                i += 2;
                while (i < cql.length() && Character.digit(cql.charAt(i), 16) >= 0) {
                    i++;
                }
                shape.append('?');
            } else {
                shape.append(c);
                i++;
            }
        }
        return shape.toString();
    }

    private static int skipQuoted(String cql, int start, char quote) {
        int i = start;
        while (i < cql.length()) {
            if (cql.charAt(i) == quote) {
                if (i + 1 < cql.length() && cql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i;
    }
}
