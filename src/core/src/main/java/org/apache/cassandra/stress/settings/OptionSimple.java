// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

class OptionSimple extends Option {

    final String displayPrefix;
    private final Pattern matchPrefix;
    private final String defaultValue;
    private final Function<String, String> valueAdapter;
    private final String description;
    private final boolean required;
    private String value;

    private static final class ValueMatcher implements Function<String, String> {
        final Pattern pattern;

        private ValueMatcher(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public String apply(String s) {
            if (!pattern.matcher(s).matches()) {
                throw new IllegalArgumentException("Invalid value " + s + "; must match pattern " + pattern);
            }
            return s;
        }
    }

    OptionSimple(String prefix, String valuePattern, String defaultValue, String description, boolean required) {
        this(
                prefix,
                Pattern.compile(Pattern.quote(prefix), Pattern.CASE_INSENSITIVE),
                Pattern.compile(valuePattern, Pattern.CASE_INSENSITIVE),
                defaultValue,
                description,
                required);
    }

    OptionSimple(
            String prefix,
            Function<String, String> valueAdapter,
            String defaultValue,
            String description,
            boolean required) {
        this(
                prefix,
                Pattern.compile(Pattern.quote(prefix), Pattern.CASE_INSENSITIVE),
                valueAdapter,
                defaultValue,
                description,
                required);
    }

    OptionSimple(
            String displayPrefix,
            Pattern matchPrefix,
            Pattern valuePattern,
            String defaultValue,
            String description,
            boolean required) {
        this(displayPrefix, matchPrefix, new ValueMatcher(valuePattern), defaultValue, description, required);
    }

    OptionSimple(
            String displayPrefix,
            Pattern matchPrefix,
            Function<String, String> valueAdapter,
            String defaultValue,
            String description,
            boolean required) {
        this.displayPrefix = displayPrefix;
        this.matchPrefix = matchPrefix;
        this.valueAdapter = valueAdapter;
        this.defaultValue = defaultValue;
        this.description = description;
        this.required = required;
    }

    @Override
    public boolean setByUser() {
        return value != null;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean present() {
        return value != null || defaultValue != null;
    }

    public String value() {
        return value != null ? value : defaultValue;
    }

    @Override
    public boolean accept(String param) {
        if (matchPrefix.matcher(param).lookingAt()) {
            if (value != null) {
                throw new IllegalArgumentException("Suboption " + displayPrefix + " has been specified more than once");
            }
            String v = param.substring(displayPrefix.length());
            value = valueAdapter.apply(v);
            assert value != null;
            return true;
        }
        return false;
    }

    @Override
    public boolean happy() {
        return !required || value != null;
    }

    @Override
    public String shortDisplay() {
        StringBuilder sb = new StringBuilder();
        if (!required) {
            sb.append('[');
        }
        sb.append(displayPrefix);
        if (displayPrefix.endsWith("=")) {
            sb.append('?');
        }
        if (displayPrefix.endsWith("<")) {
            sb.append('?');
        }
        if (displayPrefix.endsWith(">")) {
            sb.append('?');
        }
        if (!required) {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public String longDisplay() {
        if ("".equals(description)
                && defaultValue == null
                && (valueAdapter instanceof ValueMatcher valueMatcher && "".equals(valueMatcher.pattern.pattern()))) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(displayPrefix);
        if (displayPrefix.endsWith("=")) {
            sb.append('?');
        }
        if (displayPrefix.endsWith("<")) {
            sb.append('?');
        }
        if (displayPrefix.endsWith(">")) {
            sb.append('?');
        }
        if (defaultValue != null) {
            sb.append(" (default=");
            sb.append(defaultValue);
            sb.append(')');
        }
        return GroupedOptions.formatLong(sb.toString(), description);
    }

    @Override
    public String getOptionAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(displayPrefix);

        if (!(displayPrefix.endsWith("=") || displayPrefix.endsWith("<") || displayPrefix.endsWith(">"))) {
            sb.append(setByUser() ? ":*set*" : ":*not set*");
        } else {
            sb.append(value == null ? defaultValue : value);
        }
        return sb.toString();
    }

    @Override
    public List<String> multiLineDisplay() {
        return List.of();
    }

    @Override
    public int hashCode() {
        return displayPrefix.hashCode();
    }

    @Override
    public boolean equals(Object that) {
        return that instanceof OptionSimple option && option.displayPrefix.equals(this.displayPrefix);
    }
}
