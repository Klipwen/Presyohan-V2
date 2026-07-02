-- Migration to add button_label to public.announcements table
ALTER TABLE public.announcements 
  ADD COLUMN IF NOT EXISTS button_label TEXT NOT NULL DEFAULT 'Close';
