CREATE OR REPLACE FUNCTION public.search_app_user(search_term text)
RETURNS TABLE (
  id uuid,
  name text,
  user_code text,
  email text,
  avatar_url text
) 
LANGUAGE plpgsql 
SECURITY DEFINER 
SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  SELECT 
    au.id,
    au.name,       -- Changed from first_name, last_name to match your table
    au.user_code,
    au.email,
    au.avatar_url  -- Added this so you can show the user's picture in the invite card
  FROM public.app_users au
  WHERE 
    -- Check matches User Code (Case Insensitive)
    LOWER(au.user_code) = LOWER(search_term)
    OR 
    -- OR matches Email (Case Insensitive)
    LOWER(au.email) = LOWER(search_term);
END;
$$;