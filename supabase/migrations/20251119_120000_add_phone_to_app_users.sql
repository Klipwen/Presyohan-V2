-- Add phone fields to app_users for profile management
ALTER TABLE public.app_users
  ADD COLUMN IF NOT EXISTS phone TEXT,
  ADD COLUMN IF NOT EXISTS phone_normalized TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS app_users_phone_normalized_unique
  ON public.app_users (phone_normalized)
  WHERE phone_normalized IS NOT NULL;

