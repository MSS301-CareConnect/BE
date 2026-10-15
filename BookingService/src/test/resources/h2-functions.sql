-- Chỉ cho test H2: giả lập hàm PostgreSQL dùng trong BookingRepository.lockScheduleKey.
CREATE ALIAS IF NOT EXISTS HASHTEXT FOR 'mss301.bookingservice.support.H2Functions.hashtext';
CREATE ALIAS IF NOT EXISTS PG_ADVISORY_XACT_LOCK FOR 'mss301.bookingservice.support.H2Functions.pgAdvisoryXactLock';
