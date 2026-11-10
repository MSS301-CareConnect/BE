# Yêu cầu thay đổi luồng Booking v3.0 (2026-11-05)

> Bổ sung cho `2026-11-03-BOOKING_FLOW_V1.md` và `2026-11-03-BOOKING_CHANGE_REQUEST_V2.md`.
> Phương án đã được duyệt ngày **2026-11-05**.

---

## 1. Tóm tắt 3 Luồng Nghiệp vụ Mới

### 1.1. Khách hàng yêu cầu đổi người chăm sóc (Customer Caregiver Change Request)
- Áp dụng khi booking đã `CONFIRMED` hoặc `IN_PROGRESS`.
- Khách chọn phạm vi: Tất cả các buổi còn lại hoặc các buổi cụ thể trong tương lai.
- Bắt buộc nhập lý do (`reasonCode`: `SKILL_MISMATCH`, `ATTITUDE_ISSUE`, `PUNCTUALITY_ISSUE`, `PERSONAL_PREFERENCE`, `OTHER`).
- Thời hạn gửi: trước giờ bắt đầu của ca gần nhất tối thiểu 12 giờ.
- Luồng duyệt: Gửi thông báo đến Center Manager để kiểm tra nhân sự và phê duyệt gán Caregiver mới phù hợp.

### 1.2. Trung tâm điều phối người thay thế khẩn cấp (Center Emergency Replacement)
- Áp dụng khi Caregiver gặp sự cố bất khả kháng (ốm đau, tai nạn...).
- Trung tâm chọn Caregiver thay thế và nhập lý do khẩn cấp.
- Hệ thống gửi Push Notification + SMS cho khách hàng kèm lý do rõ ràng và hồ sơ năng lực của người mới.
- Khách hàng có 3 quyền lựa chọn:
  1. Đồng ý người mới (hoặc tự động đồng ý nếu không phản hồi trước ca 1 giờ).
  2. Dời lịch sang ngày khác.
  3. Từ chối người thay thế & Hủy buổi đó (Khách được hoàn 100% tiền của buổi đó).

### 1.3. Gói dài ngày: Dời lịch (Reschedule) & Hủy buổi lẻ / Hoàn tiền
- **Dời lịch (Reschedule):** Cho phép dời buổi sang ngày khác trước tối thiểu 24 giờ mà không mất phí. Giới hạn 2-4 lần tùy độ dài gói dịch vụ.
- **Hủy buổi lẻ & Hoàn tiền đa tầng:**
  - Báo trước ≥ 24 giờ: Hoàn **100%** giá trị buổi đó.
  - Báo trước từ 2 giờ đến < 24 giờ: Hoàn **70%**, 30% phí phạt chuyển làm thù lao cho Caregiver (`deposit_forfeitures`).
  - Báo trước < 2 giờ (sát giờ): Không hoàn tiền (0%), Caregiver nhận thù lao ca trực.

---

## 2. Thay đổi Cơ sở dữ liệu (Schema Changes)

Tạo script: `schema/changes/2026-11-05_booking_spec_v3_reassignment_reschedule.sql`

```sql
BEGIN;

-- 1. Bảng yêu cầu đổi caregiver từ khách hàng
CREATE TABLE IF NOT EXISTS caregiver_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_user_id UUID NOT NULL,
    center_id UUID NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    from_date DATE,
    reason_code VARCHAR(50) NOT NULL,
    reason_detail TEXT,
    preferred_caregiver_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_change_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_change_request_scope CHECK (scope_type IN ('ALL_REMAINING', 'SPECIFIC_OCCURRENCES', 'FROM_DATE'))
);

CREATE INDEX IF NOT EXISTS idx_change_requests_booking ON caregiver_change_requests(booking_id);
CREATE INDEX IF NOT EXISTS idx_change_requests_center_status ON caregiver_change_requests(center_id, status);

CREATE TABLE IF NOT EXISTS caregiver_change_request_occurrences (
    request_id UUID NOT NULL REFERENCES caregiver_change_requests(id) ON DELETE CASCADE,
    occurrence_id UUID NOT NULL REFERENCES booking_occurrences(id),
    PRIMARY KEY (request_id, occurrence_id)
);

-- 2. Bổ sung trường cho booking_occurrences
ALTER TABLE booking_occurrences 
    ADD COLUMN IF NOT EXISTS reschedule_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS original_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS original_end_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_emergency_replacement BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS replacement_reason TEXT,
    ADD COLUMN IF NOT EXISTS replacement_consent_status VARCHAR(30) DEFAULT 'NONE';

ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_occurrences_consent_status;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_occurrences_consent_status 
    CHECK (replacement_consent_status IN ('NONE', 'PENDING_CUSTOMER', 'ACCEPTED', 'REJECTED_RESCHEDULE', 'REJECTED_CANCEL'));

-- 3. Cập nhật booking_events.event_type
ALTER TABLE booking_events DROP CONSTRAINT IF EXISTS ck_booking_events_type;
ALTER TABLE booking_events ADD CONSTRAINT ck_booking_events_type CHECK (event_type IN (
    'OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED', 'CAREGIVER_CHANGED', 
    'NO_SHOW_REPORTED', 'SERVICE_ADDRESS_CHANGED',
    'CAREGIVER_CHANGE_REQUESTED', 'CAREGIVER_CHANGE_APPROVED', 'CAREGIVER_CHANGE_REJECTED',
    'EMERGENCY_REPLACEMENT_ASSIGNED', 'OCCURRENCE_RESCHEDULED'
));

-- 4. Cập nhật notifications.type
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'BOOKING_CREATED','BOOKING_ACCEPTED','BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED',
    'OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED','CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED',
    'REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED','BOOKING_COMPLETED','PAYMENT_SUCCEEDED',
    'REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED','REVIEW_CREATED',
    'REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER','RECIPIENT_INVITATION',
    'BOOKING_EXPIRED','OUTBOX_FAILED','NO_SHOW_AUTO_ACCEPTED','SESSION_AUTO_CLOSED','CAREGIVER_REQUEST',
    'BOOKING_HANDED_OVER','BOOKING_ADDRESS_CHANGED',
    'CAREGIVER_CHANGE_REQUEST_CREATED','CAREGIVER_CHANGE_REQUEST_DECIDED',
    'EMERGENCY_REPLACEMENT_NOTIFIED','OCCURRENCE_RESCHEDULED_NOTIFICATION'
));

COMMIT;
```

---

## 3. Các API Endpoints Mới & Điều Chỉnh

1. **Khách hàng gửi yêu cầu đổi caregiver:**
   - `POST /api/v1/bookings/{id}/caregiver-change-requests`
   - `GET /api/v1/bookings/{id}/caregiver-change-requests`
   - `GET /api/v1/caregiver-change-requests?centerId=&status=&page=&size=` (Manager)
   - `POST /api/v1/caregiver-change-requests/{id}/approve` (Manager)
   - `POST /api/v1/caregiver-change-requests/{id}/reject` (Manager)

2. **Trung tâm điều phối khẩn cấp:**
   - `POST /api/v1/bookings/{id}/emergency-reassignment` (Manager)
   - `POST /api/v1/booking-occurrences/{id}/replacement-consent` (Customer: ACCEPT / RESCHEDULE / REJECT_AND_CANCEL)

3. **Dời lịch buổi lẻ (Reschedule):**
   - `POST /api/v1/booking-occurrences/{id}/reschedule-preview`
   - `POST /api/v1/booking-occurrences/{id}/reschedule`
