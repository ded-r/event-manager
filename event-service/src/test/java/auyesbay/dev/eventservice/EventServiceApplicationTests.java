package auyesbay.dev.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Full application context + real PostgreSQL (Liquibase runs, Hibernate validate accepts the schema)
@SpringBootTest
@Testcontainers
class EventServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    @Test
    void contextLoads() {
    }
}
