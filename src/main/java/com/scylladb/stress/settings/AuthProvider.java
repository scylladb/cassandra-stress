package com.scylladb.stress.settings;

import com.scylladb.stress.util.JavaDriverV4SessionBuilder;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;

public class AuthProvider {
    String authClassName;
    String username;
    String password;

    public AuthProvider(String authClassName, String username, String password) {
        this.authClassName = authClassName;
        this.username = username;
        this.password = password;
    }

    public String getClassName() {
        return authClassName;
    }

    public com.datastax.driver.core.AuthProvider ToJavaDriverV3() {
        if (authClassName == null || authClassName.isEmpty()) {
            return null;
        }

        try {
            if (authClassName.equals("com.datastax.driver.core.PlainTextAuthProvider") || authClassName.equals("PlainTextAuthProvider")) {
                return new com.datastax.driver.core.PlainTextAuthProvider(username, password);
            }
            throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to initialize authentication class: " + authClassName, e);
        }
    }

    public JavaDriverV4SessionBuilder ToJavaDriverV4() {
        if (authClassName == null || authClassName.isEmpty()) {
            return null;
        }
        if (authClassName.equals("com.datastax.driver.core.PlainTextAuthProvider") ||
                authClassName.equals("PlainTextAuthProvider") ||
                authClassName.equals("com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider") ||
                authClassName.equals("ProgrammaticPlainTextAuthProvider")
        ) {
            return builder -> builder.
                    withAuthProvider(new com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider(username, password));
        }
        throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
    }
}
