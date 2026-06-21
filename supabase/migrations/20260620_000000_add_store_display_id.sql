-- Migration: Add custom display_id to stores table
-- format: SID[YY]-[SEQ] (e.g. SID25-0009, resets yearly)

-- 1. Add display_id column if not exists
ALTER TABLE public.stores ADD COLUMN IF NOT EXISTS display_id TEXT UNIQUE;

-- 2. Backfill function/script to assign display_id to existing rows
DO $$
DECLARE
  r RECORD;
  v_year TEXT;
  v_seq INTEGER;
  v_display_id TEXT;
BEGIN
  FOR r IN (SELECT id, created_at FROM public.stores WHERE display_id IS NULL ORDER BY created_at ASC) LOOP
    v_year := to_char(COALESCE(r.created_at, NOW()), 'YY');
    
    SELECT COALESCE(MAX(SUBSTRING(display_id FROM '-([0-9]+)$')::INTEGER), 0) + 1
    INTO v_seq
    FROM public.stores
    WHERE display_id LIKE 'SID' || v_year || '-%';
    
    v_display_id := 'SID' || v_year || '-' || lpad(v_seq::text, 4, '0');
    
    UPDATE public.stores
    SET display_id = v_display_id
    WHERE id = r.id;
  END LOOP;
END $$;

-- 3. Create trigger function to automatically generate display_id on insert
CREATE OR REPLACE FUNCTION public.generate_store_display_id()
RETURNS TRIGGER AS $$
DECLARE
  v_year TEXT;
  v_seq INTEGER;
BEGIN
  IF NEW.display_id IS NULL THEN
    v_year := to_char(COALESCE(NEW.created_at, NOW()), 'YY');
    
    SELECT COALESCE(MAX(SUBSTRING(display_id FROM '-([0-9]+)$')::INTEGER), 0) + 1
    INTO v_seq
    FROM public.stores
    WHERE display_id LIKE 'SID' || v_year || '-%';
    
    NEW.display_id := 'SID' || v_year || '-' || lpad(v_seq::text, 4, '0');
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Attach trigger
DROP TRIGGER IF EXISTS trg_generate_store_display_id ON public.stores;
CREATE TRIGGER trg_generate_store_display_id
BEFORE INSERT ON public.stores
FOR EACH ROW
EXECUTE FUNCTION public.generate_store_display_id();
