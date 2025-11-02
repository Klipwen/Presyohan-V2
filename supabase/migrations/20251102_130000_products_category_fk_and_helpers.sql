-- Migration: Add FK on products.category_id to categories(id) with ON DELETE CASCADE
-- and ensure idempotency. Also add helpful index.

DO $$
BEGIN
  -- Add FK if missing
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints tc
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'products'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND tc.constraint_name = 'products_category_fk'
  ) THEN
    ALTER TABLE public.products
      ADD CONSTRAINT products_category_fk
      FOREIGN KEY (category_id)
      REFERENCES public.categories(id)
      ON DELETE CASCADE;
  END IF;

  -- Ensure indexes exist for performance
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public' AND tablename = 'products' AND indexname = 'products_store_category_idx'
  ) THEN
    CREATE INDEX products_store_category_idx ON public.products (store_id, category_id);
  END IF;
END $$;

COMMENT ON CONSTRAINT products_category_fk ON public.products IS 'Products link to categories; cascading delete on category removal.';