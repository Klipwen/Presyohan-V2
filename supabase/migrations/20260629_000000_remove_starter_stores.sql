-- Migration: Remove Default Starter Store Auto-Subscription, Status Update, and Drop is_default_standard Column
-- Recreates the handle_new_user() trigger function without the auto-subscription insert.

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  -- 1. Provision profile row in public.app_users
  INSERT INTO public.app_users (id, email, name, avatar_url, role, created_at)
  VALUES (
    NEW.id,
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'name', NEW.raw_user_meta_data->>'full_name'),
    COALESCE(
      NEW.raw_user_meta_data->>'avatar_url',
      NEW.raw_user_meta_data->>'picture',
      NEW.raw_user_meta_data->>'photoURL'
    ),
    'user',
    NOW()
  )
  ON CONFLICT (id) DO UPDATE
  SET 
    name = COALESCE(EXCLUDED.name, public.app_users.name),
    avatar_url = COALESCE(EXCLUDED.avatar_url, public.app_users.avatar_url);

  -- Note: Auto-subscription to active default starter store has been disabled/removed.

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- 2. Drop the old three-argument status update RPC function if it exists
DROP FUNCTION IF EXISTS public.update_standard_store_status(UUID, BOOLEAN, BOOLEAN);

-- 3. Recreate the status update RPC function to only modify is_public
CREATE OR REPLACE FUNCTION public.update_standard_store_status(
  p_store_id UUID,
  p_is_public BOOLEAN
)
RETURNS VOID AS $$
BEGIN
  UPDATE public.stores
  SET 
    is_public = p_is_public
  WHERE id = p_store_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

GRANT EXECUTE ON FUNCTION public.update_standard_store_status(UUID, BOOLEAN) TO authenticated;

-- 4. Drop the is_default_standard column from the stores table
ALTER TABLE public.stores DROP COLUMN IF EXISTS is_default_standard;

