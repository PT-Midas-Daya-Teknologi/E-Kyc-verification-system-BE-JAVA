ALTER TABLE public.user_document
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE public.user_document
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);

ALTER TABLE public.user_document
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE public.user_document
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
