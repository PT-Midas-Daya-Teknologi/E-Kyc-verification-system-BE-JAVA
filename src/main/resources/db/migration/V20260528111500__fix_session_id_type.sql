ALTER TABLE user_document
ALTER COLUMN session_id TYPE uuid
USING session_id::uuid;