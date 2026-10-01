package mss301.identityservice.repository;

import jakarta.persistence.LockModeType;
import mss301.identityservice.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleCodeAndDeletedAtIsNull(String roleCode);

    Optional<Role> findByIdAndDeletedAtIsNull(UUID id);

    List<Role> findAllByDeletedAtIsNull();

    // Khóa dòng role làm mutex: các thao tác có thể làm mất ADMIN cuối cùng chạy tuần tự.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.roleCode = :roleCode")
    Optional<Role> findForUpdate(@Param("roleCode") String roleCode);
}
