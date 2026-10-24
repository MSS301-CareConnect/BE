-- Nếu môi trường dùng client_id khác, thay hai giá trị đích trước khi áp script.
BEGIN;
UPDATE media_files SET source_service = 'provider' WHERE source_service = 'PROVIDER_SERVICE';
UPDATE media_files SET source_service = 'booking' WHERE source_service = 'BOOKING_SERVICE';
COMMIT;
