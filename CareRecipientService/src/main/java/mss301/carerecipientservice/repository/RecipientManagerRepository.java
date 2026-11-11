package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientManagerRepository extends JpaRepository<RecipientManager, UUID>,
        JpaSpecificationExecutor<RecipientManager> {
    // Quan hệ của user với hồ sơ chưa xóa mềm (nạp sẵn hồ sơ).
    @Query("""
            select m from RecipientManager m join fetch m.recipient r
            where r.id = :recipientId and m.userId = :userId and r.deletedAt is null
            """)
    Optional<RecipientManager> findMembership(@Param("recipientId") UUID recipientId, @Param("userId") UUID userId);

    List<RecipientManager> findByRecipientIdOrderByCreatedAtAsc(UUID recipientId);

    Optional<RecipientManager> findByIdAndRecipientId(UUID id, UUID recipientId);

    Optional<RecipientManager> findByRecipientIdAndPrimaryTrue(UUID recipientId);

    boolean existsByRecipientIdAndUserId(UUID recipientId, UUID userId);

    long countByRecipientIdAndAccessLevel(UUID recipientId, RecipientAccessLevel accessLevel);

    // Danh sách "hồ sơ tôi quản lý": nạp sẵn hồ sơ trong cùng query.
    @Override
    @EntityGraph(attributePaths = "recipient")
    Page<RecipientManager> findAll(Specification<RecipientManager> specification, Pageable pageable);
}
