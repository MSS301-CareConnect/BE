package mss301.providerservice.repository;

import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByIdAndDeletedAtIsNull(UUID id);
    List<Certificate> findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(UUID caregiverId);
    List<Certificate> findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
            UUID caregiverId, VerificationStatus verificationStatus);
}
