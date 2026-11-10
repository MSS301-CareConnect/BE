-- 2026-11-05 — Vòng đời ca làm việc (ProviderService). CHỈ THÊM, chạy được nhiều lần.
-- Chạy trên database CARECONNECT_PROVIDER trước khi deploy bản ProviderService có POST /work-shifts/{id}/cancel.

-- 1. Hủy ca bắt buộc lý do; lý do được ghi lại cho caregiver đã đăng ký (đăng ký chờ duyệt → REJECTED cùng lý do).
ALTER TABLE work_shifts ADD COLUMN IF NOT EXISTS cancellation_reason varchar(500);
ALTER TABLE work_shifts ADD COLUMN IF NOT EXISTS cancelled_at timestamptz;
ALTER TABLE work_shifts ADD COLUMN IF NOT EXISTS max_bookings integer NOT NULL DEFAULT 2 CHECK (max_bookings > 0);

-- 2. Đưa trạng thái đã lưu về đúng trạng thái hiệu lực một lần (job WorkShiftLifecycleScheduler làm tiếp mỗi phút).
--    Giờ ca là giờ Việt Nam: work_date + time so với now() quy đổi sang Asia/Ho_Chi_Minh.
UPDATE work_shifts
SET shift_status = 'COMPLETED', version = version + 1, updated_at = now()
WHERE deleted_at IS NULL
  AND shift_status IN ('OPEN', 'CLOSED')
  AND (work_date + end_time) <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

UPDATE work_shifts
SET shift_status = 'CLOSED', version = version + 1, updated_at = now()
WHERE deleted_at IS NULL
  AND shift_status = 'OPEN'
  AND ((work_date + start_time) <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')
       OR registration_deadline <= now());

UPDATE shift_registrations r
SET registration_status = 'REJECTED',
    rejection_reason    = 'Ca đã bắt đầu trước khi đăng ký được duyệt',
    reviewed_at         = now(),
    version             = r.version + 1,
    updated_at          = now()
FROM work_shifts s
WHERE r.work_shift_id = s.work_shift_id
  AND r.deleted_at IS NULL
  AND r.registration_status = 'PENDING'
  AND (s.work_date + s.start_time) <= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh');

-- 3. (TÙY CHỌN — chủ dự án tự quyết) Dọn dữ liệu thử nghiệm lọt vào từ giá trị mặc định của Swagger.
-- UPDATE work_shifts SET note = NULL, version = version + 1, updated_at = now()
-- WHERE lower(trim(note)) = 'string';
-- UPDATE work_shifts SET shift_type = 'CUSTOM', version = version + 1, updated_at = now()
-- WHERE shift_type = 'STRING';
