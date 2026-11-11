package mss301.providerservice.repository.projection;

import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;

// Projection của CertificateRepository.countPendingAuditByRisk.
public interface CertificateRiskCount {
    CertificateRiskLevel getRiskLevel();

    CertificateAiCheckStatus getCheckStatus();

    long getTotal();
}
