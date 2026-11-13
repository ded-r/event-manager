package auyesbay.dev.eventservice.entity;

import auyesbay.dev.eventservice.config.JpaAuditingConfig;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class LocationPersistenceTest {

    @Container
    @ServiceConnection
    public static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    public final TestEntityManager testEntityManager;

    @Autowired
    public LocationPersistenceTest(TestEntityManager testEntityManager) {
        this.testEntityManager = testEntityManager;
    }

    // Test 1
    @Test
    void shouldPersistLocationAndFillGeneratedFields() {
        Location location = new Location();
        location.setName("Locations test name");
        location.setAddress("Locations test address");
        location.setCity("Almaty");
        location.setCountry("KZ");
        location.setCapacity(100);

        testEntityManager.persistAndFlush(location);

        assertNotNull(location.getId());
        assertNotNull(location.getCreatedAt());
        assertNotNull(location.getUpdatedAt());
        assertEquals(0L, location.getVersion());
    }

    // Test 2
    @Test
    void shouldGenerateDifferentIds() {
        Location location1 = new Location();
        location1.setName("Locations test name 1");
        location1.setAddress("Locations test address");
        location1.setCity("Almaty");
        location1.setCountry("KZ");
        location1.setCapacity(100);

        Location location2 = new Location();
        location2.setName("Locations test name 2");
        location2.setAddress("Locations test address");
        location2.setCity("Almaty");
        location2.setCountry("KZ");
        location2.setCapacity(100);

        testEntityManager.persistAndFlush(location1);
        testEntityManager.persistAndFlush(location2);

        assertNotNull(location1.getId());
        assertNotNull(location2.getId());
        assertNotEquals(location1.getId(), location2.getId());
    }

    // Test 3
    @Test
    void shouldRejectDuplicateNameAndAddress() {
        Location location1 = new Location();
        location1.setName("Locations test name");
        location1.setAddress("Locations test address");
        location1.setCity("Almaty");
        location1.setCountry("KZ");
        location1.setCapacity(100);

        testEntityManager.persistAndFlush(location1);

        Location location2 = new Location();
        location2.setName("Locations test name");
        location2.setAddress("Locations test address");
        location2.setCity("Astana");
        location2.setCountry("KG");
        location2.setCapacity(200);

        assertThatThrownBy(() -> testEntityManager.persistAndFlush(location2))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uq_locations_name_address");
    }

    // Test 4
    @Test
    void shouldAllowSameNameDifferentAddress() {
        Location location1 = new Location();
        location1.setName("Locations test name");
        location1.setAddress("Locations test address 1");
        location1.setCity("Almaty");
        location1.setCountry("KZ");
        location1.setCapacity(100);

        Location location2 = new Location();
        location2.setName("Locations test name");
        location2.setAddress("Locations test address 2");
        location2.setCity("Astana");
        location2.setCountry("KG");
        location2.setCapacity(200);

        assertThatCode(() -> {
            testEntityManager.persistAndFlush(location1);
            testEntityManager.persistAndFlush(location2);
        }).doesNotThrowAnyException();

        assertNotNull(location1.getId());
        assertNotNull(location2.getId());
    }

    // Test 5
    @Test
    void shouldRejectNonPositiveCapacity() {
        Location location = new Location();
        location.setName("Locations test name");
        location.setAddress("Locations test address");
        location.setCity("Almaty");
        location.setCountry("KZ");
        location.setCapacity(0);


        assertThatThrownBy(() -> testEntityManager.persistAndFlush(location))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("ck_locations_capacity_positive");
    }

    // Test 6
    @Test
    void shouldRejectNullName() {
        Location location = new Location();
        location.setName(null);
        location.setAddress("Locations test address");
        location.setCity("Almaty");
        location.setCountry("KZ");
        location.setCapacity(100);

        assertThatThrownBy(() -> testEntityManager.persistAndFlush(location))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("null value in column \"name\"");
    }

    // Test 7
    @Test
    void shouldBumpVersionAndUpdatedAtOnChange() {
        Location location = new Location();
        location.setName("Name");
        location.setAddress("Address");
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(100);

        testEntityManager.persistAndFlush(location);

        Instant oldUpdatedAt = location.getUpdatedAt();
        Instant oldCreatedAt = location.getCreatedAt();
        Long oldVersion = location.getVersion();

        location.setCity("New City");

        testEntityManager.flush();

        assertEquals(oldVersion + 1, location.getVersion());
        assertEquals(location.getCreatedAt(), oldCreatedAt);
        assertTrue(location.getUpdatedAt().isAfter(oldUpdatedAt));
    }

    // Test 8
    @Test
    void shouldHaveIdBasedEquality() {
        Location location1 = new Location();
        location1.setName("Name");
        location1.setAddress("Address");
        location1.setCity("City");
        location1.setCountry("KZ");
        location1.setCapacity(100);

        Location location2 = new Location();
        location2.setName("Name");
        location2.setAddress("Address");
        location2.setCity("City");
        location2.setCountry("KZ");
        location2.setCapacity(100);

        assertNotEquals(location1, location2);
    }
}
