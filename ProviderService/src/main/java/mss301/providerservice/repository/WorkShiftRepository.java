package mss301.providerservice.repository;

import jakarta.persistence.LockModeType;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// PostgreSQL không suy ra kiểu của tham số null trong "? is null" (42P18) nên tham số tùy chọn được cast tường minh.
public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {
    Optional<WorkShift> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkShift s where s.id = :id and s.deletedAt is null")
    Optional<WorkShift> findByIdForUpdate(@Param("id") UUID id);

    // excludedStatus: caregiver không thấy ca DRAFT (null = không loại trạng thái nào).
    @Query("""
            select s from WorkShift s
            where s.deletedAt is null
              and (:centerId is null or s.center.id = :centerId)
              and (cast(:status as String) is null or s.shiftStatus = :status)
              and (cast(:excludedStatus as String) is null or s.shiftStatus <> :excludedStatus)
              and (cast(:fromDate as LocalDate) is null or s.workDate >= :fromDate)
              and (cast(:toDate as LocalDate) is null or s.workDate <= :toDate)
            """)
    Page<WorkShift> search(@Param("centerId") UUID centerId,
                           @Param("status") ShiftStatus status,
                           @Param("excludedStatus") ShiftStatus excludedStatus,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate,
                           Pageable pageable);

    /**
     * Danh sách theo trạng thái HIỆU LỰC (util/WorkShiftStates), không theo cột đã lưu: openOnly / closedOnly /
     * completedOnly tính từ ngày-giờ ca (giờ VN: today + nowTime) và hạn đăng ký (now); storedStatus cho DRAFT /
     * CANCELLED. vacancyOnly: còn chỗ (số APPROVED nhỏ hơn capacity).
     */
    @Query("""
            select s from WorkShift s
            where s.deletedAt is null
              and (:centerId is null or s.center.id = :centerId)
              and (cast(:excludedStatus as String) is null or s.shiftStatus <> :excludedStatus)
              and (cast(:fromDate as LocalDate) is null or s.workDate >= :fromDate)
              and (cast(:toDate as LocalDate) is null or s.workDate <= :toDate)
              and (cast(:shiftType as String) is null or s.shiftType = :shiftType)
              and (cast(:storedStatus as String) is null or s.shiftStatus = :storedStatus)
              and (:openOnly = false or (s.shiftStatus = mss301.providerservice.enums.ShiftStatus.OPEN
                   and (s.workDate > :today or (s.workDate = :today and s.startTime > :nowTime))
                   and (s.registrationDeadline is null or s.registrationDeadline > :now)))
              and (:closedOnly = false or ((s.workDate > :today or (s.workDate = :today and s.endTime > :nowTime))
                   and (s.shiftStatus = mss301.providerservice.enums.ShiftStatus.CLOSED
                        or (s.shiftStatus = mss301.providerservice.enums.ShiftStatus.OPEN
                            and ((s.workDate < :today or (s.workDate = :today and s.startTime <= :nowTime)) or s.registrationDeadline <= :now)))))
              and (:completedOnly = false or (s.shiftStatus = mss301.providerservice.enums.ShiftStatus.COMPLETED
                   or (s.shiftStatus in (mss301.providerservice.enums.ShiftStatus.OPEN, mss301.providerservice.enums.ShiftStatus.CLOSED) and (s.workDate < :today or (s.workDate = :today and s.endTime <= :nowTime)))))
              and (:vacancyOnly = false or (select count(r) from ShiftRegistration r
                   where r.workShift = s and r.deletedAt is null
                     and r.registrationStatus = mss301.providerservice.enums.RegistrationStatus.APPROVED) < s.capacity)
              and (:pendingOnly = false or exists (select r from ShiftRegistration r
                   where r.workShift = s and r.deletedAt is null
                     and r.registrationStatus = mss301.providerservice.enums.RegistrationStatus.PENDING))
            """)
    Page<WorkShift> searchEffective(@Param("centerId") UUID centerId,
                                    @Param("excludedStatus") ShiftStatus excludedStatus,
                                    @Param("fromDate") LocalDate fromDate,
                                    @Param("toDate") LocalDate toDate,
                                    @Param("shiftType") String shiftType,
                                    @Param("storedStatus") ShiftStatus storedStatus,
                                    @Param("openOnly") boolean openOnly,
                                    @Param("closedOnly") boolean closedOnly,
                                    @Param("completedOnly") boolean completedOnly,
                                    @Param("vacancyOnly") boolean vacancyOnly,
                                    @Param("pendingOnly") boolean pendingOnly,
                                    @Param("today") LocalDate today,
                                    @Param("nowTime") LocalTime nowTime,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    // Thống kê ca (GET /work-shifts/statistics): mọi ca của một trung tâm trong bộ lọc, không phân trang.
    @Query("""
            select s from WorkShift s
            where s.deletedAt is null
              and s.center.id = :centerId
              and (cast(:fromDate as LocalDate) is null or s.workDate >= :fromDate)
              and (cast(:toDate as LocalDate) is null or s.workDate <= :toDate)
              and (cast(:shiftType as String) is null or s.shiftType = :shiftType)
            """)
    List<WorkShift> findForStatistics(@Param("centerId") UUID centerId,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate,
                                      @Param("shiftType") String shiftType);

    // Ca chưa hủy của trung tâm bắt đầu trong [fromDate, toDate] — lọc giờ chính xác trong service.
    @Query("""
            select s from WorkShift s
            where s.deletedAt is null
              and s.center.id = :centerId
              and s.shiftStatus <> mss301.providerservice.enums.ShiftStatus.CANCELLED
              and s.workDate between :fromDate and :toDate
            """)
    List<WorkShift> findActiveBetween(@Param("centerId") UUID centerId,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);

    // Job vòng đời (WorkShiftLifecycleScheduler). Câu UPDATE hàng loạt bỏ qua @Version nên tự tăng version.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkShift s
            set s.shiftStatus = mss301.providerservice.enums.ShiftStatus.COMPLETED, s.version = s.version + 1, s.updatedAt = :now
            where s.deletedAt is null
              and s.shiftStatus in (mss301.providerservice.enums.ShiftStatus.OPEN, mss301.providerservice.enums.ShiftStatus.CLOSED)
              and (s.workDate < :today or (s.workDate = :today and s.endTime <= :nowTime))
            """)
    int completeEnded(@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkShift s
            set s.shiftStatus = mss301.providerservice.enums.ShiftStatus.CLOSED, s.version = s.version + 1, s.updatedAt = :now
            where s.deletedAt is null
              and s.shiftStatus = mss301.providerservice.enums.ShiftStatus.OPEN
              and ((s.workDate < :today or (s.workDate = :today and s.startTime <= :nowTime)) or s.registrationDeadline <= :now)
            """)
    int closeExpiredOpen(@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime,
                         @Param("now") Instant now);
}
