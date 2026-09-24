-- Migration: Public Product Full-Text Search GIN Index for Sprint 2 Search-First Experience
-- Location: supabase/migrations/20260924_010000_add_products_public_search_idx.sql
-- Author: Presyohan Engineering
-- Date: 2026-09-24

CREATE INDEX IF NOT EXISTS products_public_search_idx 
ON public.products USING gin(to_tsvector('english', name)) 
WHERE is_public = true;
