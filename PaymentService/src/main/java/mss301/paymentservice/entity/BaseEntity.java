package mss301.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name="created_at", nullable=false, updatable=false)
    Instant createdAt;

    @LastModifiedDate
    @Column(name="updated_at", nullable=false)
    Instant updatedAt;
}
