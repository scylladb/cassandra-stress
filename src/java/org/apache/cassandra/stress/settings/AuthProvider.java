package org.apache.cassandra.stress.settings;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.apache.cassandra.stress.util.JavaDriverV4SessionBuilder;

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

    public com.datastax.driver.core.AuthProvider toJavaDriverV3() {
        if (authClassName == null || authClassName.isEmpty()) {
            return null;
        }

        try {
            if ("com.datastax.driver.core.PlainTextAuthProvider".equals(authClassName)
                    || "PlainTextAuthProvider".equals(authClassName)) {
                return new com.datastax.driver.core.PlainTextAuthProvider(username, password);
            }
            throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to initialize authentication class: " + authClassName, e);
        }
    }

    public JavaDriverV4SessionBuilder toJavaDriverV4() {
        if (authClassName == null || authClassName.isEmpty()) {
            return null;
        }
        if ("com.datastax.driver.core.PlainTextAuthProvider".equals(authClassName)
                || "PlainTextAuthProvider".equals(authClassName)
                || "com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider".equals(authClassName)
                || "ProgrammaticPlainTextAuthProvider".equals(authClassName)) {
            return new JavaDriverV4SessionBuilder() {
                @Override
                public CqlSessionBuilder apply(CqlSessionBuilder builder) {
                    return builder.withAuthProvider(
                            new com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider(
                                    username, password));
                }
            };
        }
        throw new IllegalArgumentException("Unknown auth provider class: " + authClassName);
    }
}
