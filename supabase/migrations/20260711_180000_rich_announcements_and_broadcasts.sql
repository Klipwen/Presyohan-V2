-- Migration: Add Rich Announcement Features
-- Add targeting, scheduling, templates, and recurrence configuration columns to announcements table.

ALTER TABLE public.announcements 
  ADD COLUMN IF NOT EXISTS template_type TEXT NOT NULL DEFAULT 'simple',
  ADD COLUMN IF NOT EXISTS template_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS targeting_type TEXT NOT NULL DEFAULT 'all',
  ADD COLUMN IF NOT EXISTS target_roles TEXT[] DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS target_store_id UUID REFERENCES public.stores(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS target_user_id UUID REFERENCES public.app_users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS start_at TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS end_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS recurrence_pattern TEXT DEFAULT 'none',
  ADD COLUMN IF NOT EXISTS recurrence_last_triggered_at TIMESTAMPTZ;

-- Create poll responses table
CREATE TABLE IF NOT EXISTS public.poll_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    announcement_id UUID REFERENCES public.announcements(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.app_users(id) ON DELETE CASCADE,
    selected_options TEXT[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ensure a user can only vote once per poll
CREATE UNIQUE INDEX IF NOT EXISTS unique_poll_response ON public.poll_responses (announcement_id, user_id);

-- Enable RLS for poll_responses
ALTER TABLE public.poll_responses ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to insert their responses
DROP POLICY IF EXISTS insert_poll_responses_auth ON public.poll_responses;
CREATE POLICY insert_poll_responses_auth ON public.poll_responses 
    FOR INSERT TO authenticated 
    WITH CHECK (auth.uid() = user_id);

-- Allow admins to read all poll responses
DROP POLICY IF EXISTS admin_all_poll_responses ON public.poll_responses;
CREATE POLICY admin_all_poll_responses ON public.poll_responses 
    FOR ALL TO authenticated 
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

-- Fetch active announcements for the logged-in user with targeting rules
CREATE OR REPLACE FUNCTION public.get_active_announcements(p_user_id UUID)
RETURNS SETOF public.announcements AS $$
BEGIN
  RETURN QUERY
  SELECT a.*
  FROM public.announcements a
  WHERE a.is_active = true
    AND NOW() BETWEEN COALESCE(a.start_at, NOW()) AND COALESCE(a.end_at, '9999-12-31'::timestamptz)
    AND (
      -- 1. All Users
      a.targeting_type = 'all'
      
      -- 2. Specific User ID
      OR (a.targeting_type = 'specific' AND a.target_user_id = p_user_id)
      
      -- 3. Specific Roles (Store Members or Sukis)
      OR (
        a.targeting_type = 'roles'
        AND (
          EXISTS (
            SELECT 1 FROM public.store_members sm
            WHERE sm.store_id = a.target_store_id
              AND sm.user_id = p_user_id
              AND sm.role = ANY(a.target_roles)
          )
          OR (
            'suki' = ANY(a.target_roles)
            AND EXISTS (
              SELECT 1 FROM public.suki_relationships sr
              WHERE sr.store_id = a.target_store_id
                AND sr.user_id = p_user_id
            )
          )
        )
      )
      
      -- 4. Store Sukis
      OR (
        a.targeting_type = 'suki'
        AND EXISTS (
          SELECT 1 FROM public.suki_relationships sr
          WHERE sr.store_id = a.target_store_id
            AND sr.user_id = p_user_id
        )
      )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
