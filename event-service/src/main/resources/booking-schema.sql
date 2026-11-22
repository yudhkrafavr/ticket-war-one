-- Event Service stores each participant's ticket offer and claim lifecycle in one row.
CREATE SCHEMA IF NOT EXISTS booking_schema;

CREATE TABLE IF NOT EXISTS booking_schema.bookings (
    id UUID PRIMARY KEY,
    war_session_id UUID NOT NULL,
    war_batch_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    ticket_number INTEGER NOT NULL,
    email_token_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL CHECK (status IN ('INVITED', 'CLAIMED', 'EXPIRED')),
    invited_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT uk_bookings_session_participant UNIQUE (war_session_id, participant_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bookings_email_token_hash
    ON booking_schema.bookings (email_token_hash)
    WHERE email_token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_batch_status
    ON booking_schema.bookings (war_session_id, war_batch_id, status);
CREATE INDEX IF NOT EXISTS idx_bookings_participant_invited
    ON booking_schema.bookings (participant_id, invited_at DESC);
CREATE INDEX IF NOT EXISTS idx_bookings_expiry
    ON booking_schema.bookings (status, expires_at);
