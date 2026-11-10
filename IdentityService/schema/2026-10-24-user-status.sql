BEGIN;
ALTER TABLE users ADD COLUMN status_reason varchar(500);
ALTER TABLE users ADD COLUMN lock_source varchar(20) CHECK (lock_source IN ('ADMIN', 'CENTER'));
UPDATE users SET lock_source = 'ADMIN' WHERE user_status = 'LOCKED';
COMMIT;
