CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_conversations_type CHECK (type IN ('PRIVATE', 'GROUP')),
    CONSTRAINT ck_group_name CHECK (type <> 'GROUP' OR (name IS NOT NULL AND length(trim(name)) > 0)),
    CONSTRAINT ck_private_name CHECK (type <> 'PRIVATE' OR name IS NULL)
);

CREATE TABLE conversation_participants (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_message_content CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_conversation_participants_user
    ON conversation_participants (user_id, conversation_id);

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);