package auyesbay.dev.eventservice.entity;

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
@Table(name = "locations")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "locations_seq")
    @SequenceGenerator(sequenceName = "locations_seq", allocationSize = 50, name = "locations_seq")
    private Long id;

    @Setter
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Setter
    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Setter
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Setter
    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Setter
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

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
        if (!(o instanceof Location location)) return false;
        if (getId() == null || location.getId() == null) return false;
        return Objects.equals(getId(), location.getId());
    }

    @Override
    public int hashCode() {
        return Location.class.hashCode();
    }
}
