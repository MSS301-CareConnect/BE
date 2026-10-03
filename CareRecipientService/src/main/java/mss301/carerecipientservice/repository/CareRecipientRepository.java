package mss301.carerecipientservice.repository;

import jakarta.persistence.LockModeType;
import mss301.carerecipientservice.entity.CareRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareRecipientRepository extends JpaRepository<CareRecipient, UUID>, JpaSpecificationExecutor<CareRecipient> {
    Optional<CareRecipient> findByIdAndDeletedAtIsNull(UUID id);

    List<CareRecipient> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    // Khóa dòng hồ sơ khi đổi danh sách người quản lý / liên hệ chính / trạng thái: các thao tác ghi tuần tự,
    // kiểm "OWNER cuối cùng" và "đúng một primary" không bị hai request song song cùng vượt qua.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CareRecipient r where r.id = :id and r.deletedAt is null")
    Optional<CareRecipient> findForUpdate(@Param("id") UUID id);
}
