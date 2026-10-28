package mss301.providerservice.repository;

import java.util.UUID;

// Projection: số chứng chỉ đã được ADMIN xác minh của mỗi caregiver (thẻ kết quả tìm kiếm — SRCH-01).
public interface CaregiverCertificateCount {

    UUID getCaregiverId();

    long getCertificateCount();
}
