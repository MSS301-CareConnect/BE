BEGIN;

CREATE TABLE chat_conversations (
                                    conversation_id uuid PRIMARY KEY,
                                    user_id uuid NOT NULL,

                                    title varchar(120) NOT NULL,

                                    conversation_status varchar(20) NOT NULL DEFAULT 'ACTIVE'
                                        CHECK (
                                            conversation_status IN (
                                                                    'ACTIVE',
                                                                    'ARCHIVED'
                                                )
                                            ),

                                    created_at timestamptz NOT NULL,
                                    updated_at timestamptz NOT NULL,
                                    deleted_at timestamptz,

                                    version bigint NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_conversations_user_updated
    ON chat_conversations (
                           user_id,
                           updated_at DESC
        )
    WHERE deleted_at IS NULL;


CREATE TABLE chat_messages (
                               chat_message_id uuid PRIMARY KEY,

                               conversation_id uuid NOT NULL
                                   REFERENCES chat_conversations (conversation_id),

                               message_role varchar(20) NOT NULL
                                   CHECK (
                                       message_role IN (
                                                        'USER',
                                                        'ASSISTANT'
                                           )
                                       ),

                               content text NOT NULL,

                               sequence_number integer NOT NULL
                                   CHECK (sequence_number > 0),

                               model_name varchar(120),

                               prompt_tokens integer
                                   CHECK (
                                       prompt_tokens IS NULL
                                           OR prompt_tokens >= 0
                                       ),

                               completion_tokens integer
                                   CHECK (
                                       completion_tokens IS NULL
                                           OR completion_tokens >= 0
                                       ),

                               created_at timestamptz NOT NULL,
                               updated_at timestamptz NOT NULL,
                               deleted_at timestamptz,

                               version bigint NOT NULL DEFAULT 0,

                               CONSTRAINT uk_chat_messages_conversation_sequence
                                   UNIQUE (
                                           conversation_id,
                                           sequence_number
                                       )
);

CREATE INDEX idx_chat_messages_conversation
    ON chat_messages (
                      conversation_id,
                      sequence_number
        )
    WHERE deleted_at IS NULL;


CREATE TABLE chat_message_media (
                                    chat_message_media_id uuid PRIMARY KEY,

                                    chat_message_id uuid NOT NULL
                                        REFERENCES chat_messages (chat_message_id),

    -- ID này thuộc MediaService, không tạo cross-service foreign key.
                                    media_file_id uuid NOT NULL,

                                    original_file_name varchar(255) NOT NULL,

                                    mime_type varchar(100) NOT NULL
                                        CHECK (
                                            mime_type IN (
                                                          'image/jpeg',
                                                          'image/png',
                                                          'image/webp'
                                                )
                                            ),

                                    sort_order smallint NOT NULL
                                        CHECK (
                                            sort_order BETWEEN 0 AND 2
                                            ),

                                    created_at timestamptz NOT NULL,
                                    updated_at timestamptz NOT NULL,
                                    deleted_at timestamptz,

                                    version bigint NOT NULL DEFAULT 0,

                                    CONSTRAINT uk_chat_message_media_order
                                        UNIQUE (
                                                chat_message_id,
                                                sort_order
                                            )
);

CREATE INDEX idx_chat_message_media_message
    ON chat_message_media (chat_message_id)
    WHERE deleted_at IS NULL;


CREATE TABLE guest_daily_usage (
                                   guest_daily_usage_id uuid PRIMARY KEY,
                                   visitor_hash varchar(64) NOT NULL,
                                   usage_date date NOT NULL,
                                   prompt_count integer NOT NULL DEFAULT 0 CHECK (prompt_count >= 0),
                                   created_at timestamptz NOT NULL,
                                   updated_at timestamptz NOT NULL,
                                   deleted_at timestamptz,
                                   version bigint NOT NULL DEFAULT 0,

                                   CONSTRAINT uk_guest_daily_usage
                                       UNIQUE (visitor_hash,usage_date)
);

CREATE INDEX idx_guest_daily_usage_date
    ON guest_daily_usage (usage_date);

COMMIT;