-- Ví 4 role + rút tiền (2026-11-09, docs/2026-11-09-WALLET_PLAN.md) cho DB ĐÃ CÓ dữ liệu.
-- DB mới dùng schema/payment.sql. Áp SAU 2026-11-03-deposit-forfeitures.sql. Chạy lại được:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-09-wallets.sql
-- Thay đổi (chỉ thêm):
--   * refunds.channel (GATEWAY | WALLET) — dòng cũ GATEWAY.
--   * occurrence_financials.settled_at — buổi đã cộng cho ví trung tâm / nền tảng.
--   * wallets, wallet_transactions (append-only), wallet_withdrawals.
-- Dữ liệu cũ (mục 7 của kế hoạch):
--   * cọc bị giữ trước đây thuộc caregiver → chuyển vào ví caregiver (FORFEIT_CREDIT, khóa FORFEIT:<occurrence>);
--   * buổi đã bắt đầu trước ngày áp script KHÔNG cộng cho trung tâm: đánh settled_at = now().
BEGIN;

ALTER TABLE refunds ADD COLUMN IF NOT EXISTS channel varchar(20) NOT NULL DEFAULT 'GATEWAY';
ALTER TABLE refunds DROP CONSTRAINT IF EXISTS ck_refunds_channel;
ALTER TABLE refunds ADD CONSTRAINT ck_refunds_channel CHECK (channel IN ('GATEWAY','WALLET'));

ALTER TABLE occurrence_financials ADD COLUMN IF NOT EXISTS settled_at timestamptz;

CREATE TABLE IF NOT EXISTS wallets (
    wallet_id         uuid          PRIMARY KEY,
    owner_type        varchar(20)   NOT NULL CONSTRAINT ck_wallets_owner_type
                                        CHECK (owner_type IN ('CUSTOMER','CAREGIVER','CENTER','PLATFORM')),
    owner_id          uuid          NOT NULL,
    available_balance numeric(14,2) NOT NULL DEFAULT 0,
    held_balance      numeric(14,2) NOT NULL DEFAULT 0 CHECK (held_balance >= 0),
    version           bigint        NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT uk_wallets_owner UNIQUE (owner_type, owner_id),
    -- Chỉ ví trung tâm được âm (hoàn khiếu nại sau khi đã cộng tiền buổi).
    CONSTRAINT ck_wallets_available CHECK (owner_type = 'CENTER' OR available_balance >= 0)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    transaction_id   uuid          PRIMARY KEY,
    wallet_id        uuid          NOT NULL REFERENCES wallets(wallet_id),
    type             varchar(30)   NOT NULL CONSTRAINT ck_wallet_transactions_type CHECK (type IN ('REFUND_CREDIT',
                                       'FORFEIT_CREDIT','SERVICE_SETTLEMENT','COMMISSION_CREDIT','COMPLAINT_DEBIT',
                                       'WITHDRAWAL_HOLD','WITHDRAWAL_RELEASE','WITHDRAWAL_PAID')),
    amount           numeric(14,2) NOT NULL CHECK (amount > 0),
    available_before numeric(14,2) NOT NULL,
    available_after  numeric(14,2) NOT NULL,
    held_before      numeric(14,2) NOT NULL,
    held_after       numeric(14,2) NOT NULL,
    center_id        uuid,
    booking_id       uuid,
    occurrence_id    uuid,
    refund_id        uuid,
    withdrawal_id    uuid,
    reference_key    varchar(150)  NOT NULL,
    note             varchar(500),
    created_at       timestamptz   NOT NULL,
    CONSTRAINT uk_wallet_transactions_reference UNIQUE (wallet_id, reference_key)
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_created ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_center_type ON wallet_transactions(center_id, type, created_at)
    WHERE center_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_booking ON wallet_transactions(booking_id)
    WHERE booking_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS wallet_withdrawals (
    withdrawal_id       uuid          PRIMARY KEY,
    wallet_id           uuid          NOT NULL REFERENCES wallets(wallet_id),
    requester_user_id   uuid          NOT NULL,
    owner_type          varchar(20)   NOT NULL CHECK (owner_type IN ('CUSTOMER','CAREGIVER','CENTER')),
    center_id           uuid,
    amount              numeric(14,2) NOT NULL CHECK (amount > 0),
    bank_name           varchar(100)  NOT NULL,
    bank_account_number varchar(30)   NOT NULL,
    account_holder_name varchar(100)  NOT NULL,
    status              varchar(20)   NOT NULL CONSTRAINT ck_wallet_withdrawals_status
                                          CHECK (status IN ('PENDING','APPROVED','REJECTED','COMPLETED')),
    reject_reason       varchar(500),
    reviewed_by         uuid,
    reviewed_at         timestamptz,
    transfer_reference  varchar(100),
    completed_by        uuid,
    completed_at        timestamptz,
    version             bigint        NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_wallet_created ON wallet_withdrawals(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_status_created ON wallet_withdrawals(status, created_at DESC);

-- Buổi đã bắt đầu trước khi có ví: không cộng cho trung tâm / nền tảng.
UPDATE occurrence_financials SET settled_at = now()
WHERE settled_at IS NULL AND final_status IS NULL AND start_at <= now();

-- Cọc bị giữ cũ của caregiver → ví caregiver. Gộp theo (caregiver, buổi) vì khóa sổ là FORFEIT:<occurrence>.
INSERT INTO wallets (wallet_id, owner_type, owner_id, available_balance, held_balance, version, created_at, updated_at)
SELECT gen_random_uuid(), 'CAREGIVER', caregiver_user_id, 0, 0, 0, now(), now()
FROM deposit_forfeitures
WHERE beneficiary = 'CAREGIVER'
GROUP BY caregiver_user_id
ON CONFLICT (owner_type, owner_id) DO NOTHING;

WITH legacy AS (
    SELECT f.caregiver_user_id, f.occurrence_id, min(f.booking_id::text)::uuid AS booking_id,
           min(f.center_id::text)::uuid AS center_id, sum(f.amount) AS amount, min(f.created_at) AS created_at
    FROM deposit_forfeitures f
    WHERE f.beneficiary = 'CAREGIVER'
    GROUP BY f.caregiver_user_id, f.occurrence_id
), pending AS (
    SELECT w.wallet_id, w.available_balance, l.*
    FROM legacy l
    JOIN wallets w ON w.owner_type = 'CAREGIVER' AND w.owner_id = l.caregiver_user_id
    WHERE NOT EXISTS (SELECT 1 FROM wallet_transactions t
                      WHERE t.wallet_id = w.wallet_id AND t.reference_key = 'FORFEIT:' || l.occurrence_id)
), running AS (
    SELECT p.*, p.available_balance + sum(p.amount) OVER (PARTITION BY p.wallet_id ORDER BY p.created_at, p.occurrence_id)
               AS balance_after
    FROM pending p
)
INSERT INTO wallet_transactions (transaction_id, wallet_id, type, amount, available_before, available_after,
                                 held_before, held_after, center_id, booking_id, occurrence_id, refund_id,
                                 withdrawal_id, reference_key, note, created_at)
SELECT gen_random_uuid(), r.wallet_id, 'FORFEIT_CREDIT', r.amount, r.balance_after - r.amount, r.balance_after,
       0, 0, r.center_id, r.booking_id, r.occurrence_id, NULL, NULL, 'FORFEIT:' || r.occurrence_id,
       'backfill 2026-11-09', r.created_at
FROM running r;

-- Số dư ví = tổng sổ (chỉ ví caregiver vừa backfill, chưa có rút tiền nên held = 0).
UPDATE wallets w SET available_balance = s.total, updated_at = now()
FROM (SELECT t.wallet_id, sum(CASE WHEN t.type IN ('REFUND_CREDIT','FORFEIT_CREDIT','SERVICE_SETTLEMENT',
                                                    'COMMISSION_CREDIT','WITHDRAWAL_RELEASE') THEN t.amount
                                   WHEN t.type IN ('COMPLAINT_DEBIT','WITHDRAWAL_HOLD') THEN -t.amount
                                   ELSE 0 END) AS total
      FROM wallet_transactions t GROUP BY t.wallet_id) s
WHERE w.wallet_id = s.wallet_id AND w.owner_type = 'CAREGIVER'
  AND NOT EXISTS (SELECT 1 FROM wallet_withdrawals d WHERE d.wallet_id = w.wallet_id);

COMMIT;
