package auyesbay.dev.eventservice.repository;

import auyesbay.dev.eventservice.domain.EventStatus;
import auyesbay.dev.eventservice.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    @EntityGraph(attributePaths = "location")
    Page<Event> findByStatus(EventStatus eventStatus, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Page<Event> findByLocationId(Long locationId, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Page<Event> findByStartTimeBetween(Instant from, Instant to, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "location")
    Page<Event> findAll(Pageable pageable);


    boolean existsByLocationId(Long locationId);
}
