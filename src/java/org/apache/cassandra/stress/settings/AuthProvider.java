// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.settings;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider;
import java.util.Set;

public class AuthProvider {
    private static final Set<String> PLAIN_TEXT_NAMES = Set.of(
            "PlainTextAuthProvider",
            "com.datastax.driver.core.PlainTextAuthProvider",
            "ProgrammaticPlainTextAuthProvider",
            "com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider");

    private final String authClassName;
    private final String username;
    private final String password;

    public AuthProvider(String authClassName, String username, String password) {
        this.authClassName = authClassName;
        this.username = username;
        this.password = password;
    }

    public String getClassName() {
        return authClassName;
    }

    public boolean isSet() {
        return authClassName != null && !authClassName.isEmpty();
    }

    public CqlSessionBuilder applyTo(CqlSessionBuilder builder) {
        if (!isSet()) return builder;
        if (!PLAIN_TEXT_NAMES.contains(authClassName))
            throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
        return builder.withAuthProvider(new ProgrammaticPlainTextAuthProvider(username, password));
    }
}
