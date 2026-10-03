package mss301.identityservice.repository;

import jakarta.persistence.LockModeType;
import mss301.identityservice.entity.User;
import mss301.identityservice.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    // Kiểm cả tài khoản đã xóa mềm: email đã dùng không được tái sử dụng (chưa có quy trình purge).
    boolean existsByEmailIgnoreCase(String email);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // Nạp sẵn role trong cùng query — batch cho service khác không được N+1 theo số user.
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    // Khóa dòng user để ghi tuần tự với khóa tài khoản / đổi mật khẩu — tránh ghi đè trạng thái cũ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findForUpdate(@Param("id") UUID id);

    @Query("""
            select count(distinct u.id) from User u join u.userRoles ur
            where u.deletedAt is null and u.status = :status
            and ur.role.roleCode = :roleCode and ur.role.active = true and ur.role.deletedAt is null
            """)
    long countActiveByRoleCode(@Param("roleCode") String roleCode, @Param("status") UserStatus status);

    // Caregiver cùng trung tâm, loại tài khoản đồng thời có role quản trị để manager không thao tác được.
    @Query("""
            select distinct u from User u join u.userRoles ur
            where u.deletedAt is null and u.centerId = :centerId
            and ur.role.roleCode = :roleCode and ur.role.active = true and ur.role.deletedAt is null
            and not exists (select privileged from UserRole privileged where privileged.user = u
                and privileged.role.roleCode in :excludedRoleCodes
                and privileged.role.active = true and privileged.role.deletedAt is null)
            """)
    Page<User> findByCenterAndRole(@Param("centerId") UUID centerId,
                                   @Param("roleCode") String roleCode,
                                   @Param("excludedRoleCodes") Collection<String> excludedRoleCodes,
                                   Pageable pageable);
}
