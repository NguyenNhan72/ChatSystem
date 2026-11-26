-- Schema co so du lieu chat server (PostgreSQL).
-- Du lieu he thong (users, conversations, members) + lich su chat (messages) trong mot DB PostgreSQL.
-- Bang messages duoc thiet ke theo cap khoa nhu sach (Hinh 12-10): (conversation_id, seq).

CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL PRIMARY KEY,
    username       TEXT        NOT NULL UNIQUE,
    display_name   TEXT        NOT NULL,
    password_hash  TEXT        NOT NULL,
    last_active_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversations (
    id         BIGSERIAL PRIMARY KEY,
    type       TEXT        NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    name       TEXT,                           -- ten nhom; NULL neu la chat 1-1
    direct_key TEXT UNIQUE,                    -- "minUserId:maxUserId" cho 1-1, tranh trung lap cuoc tro chuyen
    last_seq   BIGINT      NOT NULL DEFAULT 0, -- bo dem sequence cuc bo cua hoi thoai (sach muc Message ID, cach 3)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversation_members (
    conversation_id    BIGINT      NOT NULL REFERENCES conversations (id),
    user_id            BIGINT      NOT NULL REFERENCES users (id),
    last_delivered_seq BIGINT      NOT NULL DEFAULT 0, -- con tro trang thai tin nhan: seq <= so nay => Da nhan (Delivered)
    last_read_seq      BIGINT      NOT NULL DEFAULT 0, -- con tro da xem: seq <= so nay => Da xem (Read)
    joined_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_members_user ON conversation_members (user_id);

CREATE TABLE IF NOT EXISTS messages (
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id), -- partition key neu dung KV store
    seq             BIGINT      NOT NULL,                               -- duy nhat va tang dan trong tung hoi thoai
    client_msg_id   UUID        NOT NULL UNIQUE,                        -- khoa chong trung lap tu client (idempotency)
    sender_id       BIGINT      NOT NULL REFERENCES users (id),
    content         TEXT        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 99999), -- gioi han < 100,000 ky tu nhu sach
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, seq)
);

-- Hinh 12-14/12-15 trong sach: message sync queue (inbox) cua tung user.
-- Moi tin nhan duoc CHEP vao inbox cua moi thanh vien hoi thoai (ke ca nguoi gui, de cac thiet bi khac cua ho dong bo).
-- inbox_id tang dan trong tung inbox; moi thiet bi giu cur_max_message_id = inbox_id lon nhat da co (Hinh 12-13).
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_inbox_id BIGINT NOT NULL DEFAULT 0; -- bo dem inbox_id cua user
CREATE TABLE IF NOT EXISTS inbox (
    user_id         BIGINT      NOT NULL REFERENCES users (id), -- nguoi nhan: chu cua inbox
    inbox_id        BIGINT      NOT NULL,
    conversation_id BIGINT      NOT NULL,
    seq             BIGINT      NOT NULL,
    client_msg_id   UUID        NOT NULL,
    sender_id       BIGINT      NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, inbox_id)
);

-- Bang luu token thiet bi Firebase (FCM) de gui push notification khi offline (Hinh 12-12 buoc 5b trong sach)
CREATE TABLE IF NOT EXISTS device_tokens (
    user_id     BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    fcm_token   TEXT   NOT NULL,
    updated_at  BIGINT NOT NULL
);
