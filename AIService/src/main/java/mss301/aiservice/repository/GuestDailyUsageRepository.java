package mss301.aiservice.repository;

import jakarta.persistence.LockModeType;
import mss301.aiservice.entity.GuestDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestDailyUsageRepository extends JpaRepository<GuestDailyUsage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GuestDailyUsage> findByVisitorHashAndUsageDateAndDeletedAtIsNull(String visitorHash, LocalDate usageDate);
}
