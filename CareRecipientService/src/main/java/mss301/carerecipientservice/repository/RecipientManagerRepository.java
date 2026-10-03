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
public interface RecipientManagerRepository extends JpaRepository<RecipientManager, UUID>, JpaSpecificationExecutor<RecipientManager> {
    // Quan hệ của user với hồ sơ chưa xóa mềm (nạp sẵn hồ sơ).
    @Query("""
            select m from RecipientManager m join fetch m.recipient r
            where r.id = :recipientId and m.userId = :userId and r.deletedAt is null
            """)
    Optional<RecipientManager> findMembership(@Param("recipientId") UUID recipientId, @Param("userId") UUID userId);

    @Query("select m from RecipientManager m where m.recipient.id = :recipientId order by m.createdAt asc")
    List<RecipientManager> findAllByRecipient(@Param("recipientId") UUID recipientId);

    @Query("select m from RecipientManager m where m.id = :id and m.recipient.id = :recipientId")
    Optional<RecipientManager> findByIdAndRecipient(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Query("select m from RecipientManager m where m.recipient.id = :recipientId and m.primary = true")
    Optional<RecipientManager> findPrimary(@Param("recipientId") UUID recipientId);

    @Query("select count(m) > 0 from RecipientManager m where m.recipient.id = :recipientId and m.userId = :userId")
    boolean existsByRecipientAndUser(@Param("recipientId") UUID recipientId, @Param("userId") UUID userId);

    @Query("select count(m) from RecipientManager m where m.recipient.id = :recipientId and m.accessLevel = :accessLevel")
    long countByRecipientAndAccessLevel(@Param("recipientId") UUID recipientId,
                                        @Param("accessLevel") RecipientAccessLevel accessLevel);

    // Danh sách "hồ sơ tôi quản lý": nạp sẵn hồ sơ trong cùng query.
    @Override
    @EntityGraph(attributePaths = "recipient")
    Page<RecipientManager> findAll(Specification<RecipientManager> specification, Pageable pageable);
}
