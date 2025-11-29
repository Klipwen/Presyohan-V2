-- Migration: Add custom readable user ID (e.g., ph25-0001)
-- 1. Create a sequence for the auto-incrementing number
CREATE SEQUENCE IF NOT EXISTS public.user_code_seq START 1;

-- 2. Create a function to generate the ID: 'ph' + YY + '-' + XXXX
CREATE OR REPLACE FUNCTION public.generate_user_code()
RETURNS TEXT AS $$
DECLARE
  year_part TEXT;
  num_part BIGINT;
  formatted_code TEXT;
BEGIN
  -- Get the last two digits of the current year (e.g., '25')
  year_part := to_char(NOW(), 'YY');
  
  -- Get the next number from the sequence
  num_part := nextval('public.user_code_seq');
  
  -- Format as phYY-XXXX (pads number with zeros to 4 digits)
  -- If number exceeds 9999, it will simply expand (e.g., ph25-10000)
  formatted_code := 'ph' || year_part || '-' || lpad(num_part::TEXT, 4, '0');
  
  RETURN formatted_code;
END;
$$ LANGUAGE plpgsql;

-- 3. Add the column to app_users with the default value calling the function
ALTER TABLE public.app_users
  ADD COLUMN IF NOT EXISTS user_code TEXT UNIQUE DEFAULT public.generate_user_code();

-- 4. Create an index for faster lookups by this code
CREATE INDEX IF NOT EXISTS app_users_user_code_idx ON public.app_users (user_code);