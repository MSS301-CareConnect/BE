-- BK-09 (2026-10-22): chống trùng lịch caregiver ngay ở DB — chốt chặn cuối khi hai instance cùng vượt bước kiểm ở service.
-- Áp SAU 2026-10-22_occurrence_cancellation.sql: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_occurrence_slot_exclusion.sql
-- Điều kiện chỉ dựa trên trạng thái BUỔI: booking rời trạng thái giữ chỗ (hủy / hết hạn / từ chối / hoàn tất) luôn kéo theo
-- mọi buổi chưa kết thúc thành CANCELLED (BookingLifecycleService), nên không cần cột active_slot riêng.
BEGIN;

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Dữ liệu cũ đã trùng thì dừng, để xử lý tay (đổi caregiver / hủy buổi) trước khi thêm ràng buộc.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM booking_occurrences a
        JOIN booking_occurrences b
          ON a.caregiver_id = b.caregiver_id
         AND a.occurrence_id < b.occurrence_id
         AND tstzrange(a.start_at, a.end_at, '[)') && tstzrange(b.start_at, b.end_at, '[)')
        WHERE a.status IN ('SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS')
          AND b.status IN ('SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS')
    ) THEN
        RAISE EXCEPTION 'booking_occurrences has overlapping active slots for the same caregiver — resolve them first';
    END IF;
END $$;

ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ex_booking_occurrences_caregiver_slot;
ALTER TABLE booking_occurrences ADD CONSTRAINT ex_booking_occurrences_caregiver_slot
    EXCLUDE USING gist (caregiver_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (status IN ('SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS'));

COMMIT;
