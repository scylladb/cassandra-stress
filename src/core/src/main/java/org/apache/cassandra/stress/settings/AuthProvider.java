// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import java.util.Set;

public class AuthProvider {
    private static final Set<String> PLAIN_TEXT_NAMES = Set.of(
            "PlainTextAuthProvider",
            "com.datastax.driver.core.PlainTextAuthProvider",
            "ProgrammaticPlainTextAuthProvider",
            "com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider");

    private final String authClassName;

    public AuthProvider(String authClassName) {
        this.authClassName = authClassName;
    }

    public String getClassName() {
        return authClassName;
    }

    public boolean isSet() {
        return authClassName != null && !authClassName.isEmpty();
    }

    public void requirePlainText() {
        if (!PLAIN_TEXT_NAMES.contains(authClassName)) {
            throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
        }
    }
}
