ALTER TABLE public.user_document
ADD COLUMN created_at TIMESTAMP;

ALTER TABLE public.user_document
ADD COLUMN created_by VARCHAR(255);

ALTER TABLE public.user_document
ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE public.user_document
ADD COLUMN updated_by VARCHAR(255);
