package com.bayflow.workshop;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One PostgreSQL 16 container shared by every integration test in this module (singleton pattern). */
public final class PostgresContainer {

    public static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
