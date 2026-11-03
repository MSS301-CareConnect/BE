package mss301.paymentservice.enums;

// CR-4 (2026-11-03): ai hưởng phần cọc khách bị giữ — buổi đã có caregiver thì caregiver hưởng toàn bộ, chưa có thì trung
// tâm. Đồng bộ CHECK deposit_forfeitures.beneficiary.
public enum ForfeitureBeneficiary {
    CENTER,
    CAREGIVER
}
