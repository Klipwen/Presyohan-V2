-- Migration: Add User Suspension Columns to app_users
-- Safe, idempotent script for Supabase Postgres.

ALTER TABLE public.app_users 
  ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN NOT NULL DEFAULT FALSE;
