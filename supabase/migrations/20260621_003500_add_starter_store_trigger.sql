-- Migration: Automatically subscribe new users to the default starter store
-- Upgrades handle_new_user() trigger function to perform store association.

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

  -- 2. Automatically subscribe the new user to the active default starter store if one exists
  INSERT INTO public.store_members (store_id, user_id, role)
  SELECT id, NEW.id, 'member'
  FROM public.stores
  WHERE is_default_standard = TRUE
  ON CONFLICT DO NOTHING;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;
