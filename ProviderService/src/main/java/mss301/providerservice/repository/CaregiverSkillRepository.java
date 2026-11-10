package mss301.providerservice.repository;

import mss301.providerservice.entity.CaregiverSkill;
import mss301.providerservice.entity.CaregiverSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaregiverSkillRepository extends JpaRepository<CaregiverSkill, CaregiverSkillId> {

    List<CaregiverSkill> findByIdCaregiverIdOrderByAssignedAtAsc(UUID caregiverId);
}
