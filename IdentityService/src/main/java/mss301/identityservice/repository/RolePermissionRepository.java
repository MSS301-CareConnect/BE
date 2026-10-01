package mss301.identityservice.repository;

import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
}
