package mss301.providerservice.repository;

import mss301.providerservice.entity.CaregiverTimeOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaregiverTimeOffRepository extends JpaRepository<CaregiverTimeOff, UUID> {

    Optional<CaregiverTimeOff> findByIdAndCaregiverIdAndDeletedAtIsNull(UUID id, UUID caregiverId);

    // Giao nửa mở [from, to): ngày nghỉ kết thúc đúng lúc from hoặc bắt đầu đúng lúc to không tính là giao.
    @Query("""
            select t from CaregiverTimeOff t
            where t.deletedAt is null
              and t.caregiver.id = :caregiverId
              and t.startAt < :to
              and t.endAt > :from
            order by t.startAt asc
            """)
    List<CaregiverTimeOff> findOverlapping(@Param("caregiverId") UUID caregiverId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    // Bản theo lô cho tìm kiếm caregiver (SRCH-01): một query cho mọi ứng viên.
    @Query("""
            select t from CaregiverTimeOff t
            where t.deletedAt is null
              and t.caregiver.id in :caregiverIds
              and t.startAt < :to
              and t.endAt > :from
            """)
    List<CaregiverTimeOff> findOverlappingForCaregivers(@Param("caregiverIds") Collection<UUID> caregiverIds,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);

    @Query("""
            select count(t) > 0 from CaregiverTimeOff t
            where t.deletedAt is null
              and t.caregiver.id = :caregiverId
              and t.startAt < :to
              and t.endAt > :from
            """)
    boolean existsOverlapping(@Param("caregiverId") UUID caregiverId,
                              @Param("from") Instant from,
                              @Param("to") Instant to);
}
