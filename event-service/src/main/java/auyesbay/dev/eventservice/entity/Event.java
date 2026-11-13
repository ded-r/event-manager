package auyesbay.dev.eventservice.entity;

import auyesbay.dev.eventservice.domain.EventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "events_seq")
    @SequenceGenerator(sequenceName = "events_seq", allocationSize = 50, name = "events_seq")
    private Long id;

    @Setter
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Setter
    @Column(name = "description")
    private String description;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Setter
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Setter
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Setter
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event event)) return false;
        if (getId() == null || event.getId() == null) return false;
        return Objects.equals(getId(), event.getId());
    }

    @Override
    public int hashCode() {
        return Event.class.hashCode();
    }
}
