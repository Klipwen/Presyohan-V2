-- Add has_password RPC function to securely check if the currently authenticated user has a password set
CREATE OR REPLACE FUNCTION public.has_password()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM auth.users
    WHERE id = auth.uid()
    AND encrypted_password IS NOT NULL
    AND encrypted_password <> ''
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.has_password() TO authenticated;
