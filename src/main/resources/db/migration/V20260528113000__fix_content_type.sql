-- Convert large-object references to bytea only when content is stored as oid.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_document'
          AND column_name = 'content'
          AND udt_name = 'oid'
    ) THEN
        ALTER TABLE user_document
            ALTER COLUMN content TYPE bytea USING lo_get(content);
    END IF;
END $$;
