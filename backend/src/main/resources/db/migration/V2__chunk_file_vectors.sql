ALTER TABLE file_vectors
    ADD COLUMN chunk_index INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN chunk_text TEXT NOT NULL DEFAULT '';

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
    WHERE t.relname = 'file_vectors'
      AND c.contype = 'u'
      AND a.attname = 'file_id'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE file_vectors DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_file_vectors_file_id ON file_vectors(file_id);
