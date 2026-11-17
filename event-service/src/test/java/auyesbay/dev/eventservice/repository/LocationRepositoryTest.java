package auyesbay.dev.eventservice.repository;

import auyesbay.dev.eventservice.config.JpaAuditingConfig;
import auyesbay.dev.eventservice.entity.Location;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class LocationRepositoryTest {

    @Container
    @ServiceConnection
    public static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16");

    public final LocationRepository locationRepository;
    public final TestEntityManager testEntityManager;

    @Autowired
    public LocationRepositoryTest(LocationRepository locationRepository, TestEntityManager testEntityManager) {
        this.locationRepository = locationRepository;
        this.testEntityManager = testEntityManager;
    }

    // Test 6
    @Test
    void existsByNameAndAddressMatchesBothFields() {
        Location location = new Location();
        location.setName("A");
        location.setAddress("B");
        location.setCity("City");
        location.setCountry("KZ");
        location.setCapacity(10);

        testEntityManager.persistAndFlush(location);
        testEntityManager.clear();

        assertThat(locationRepository.existsByNameAndAddress("A", "B")).isTrue();
        assertThat(locationRepository.existsByNameAndAddress("A", "X")).isFalse();   // same name, other address
        assertThat(locationRepository.existsByNameAndAddress("X", "B")).isFalse();   // other name, same address
    }
}
