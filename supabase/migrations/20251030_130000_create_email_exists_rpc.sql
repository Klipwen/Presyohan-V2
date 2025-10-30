-- Create a SECURITY DEFINER RPC to check if an email already exists
-- in auth.users. This allows anonymous/frontend clients to perform
-- availability checks without exposing sensitive data.

CREATE OR REPLACE FUNCTION public.email_exists(p_email TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_exists BOOLEAN;
BEGIN
  SELECT EXISTS (SELECT 1 FROM auth.users u WHERE lower(u.email) = lower(p_email)) INTO v_exists;
  RETURN v_exists;
END;
$$;

COMMENT ON FUNCTION public.email_exists(TEXT) IS 'Returns true if an auth user exists with the given email (case-insensitive).';