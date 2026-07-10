-- Migration: Add Contact Us Info, App Ratings, and Feedback Threading Tables
-- Safe, idempotent script for Supabase Postgres.

-- 1. Create app_contact_info table
CREATE TABLE IF NOT EXISTS public.app_contact_info (
    id TEXT PRIMARY KEY DEFAULT 'default' CHECK (id = 'default'),
    location TEXT NOT NULL DEFAULT 'Curva Medellin, Cebu City, Philippines',
    email TEXT NOT NULL DEFAULT 'presyohan@gmail.com',
    number TEXT NOT NULL DEFAULT '+639 430 8387',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES public.app_users(id) ON DELETE SET NULL
);

-- Seed default contact row if not exists
INSERT INTO public.app_contact_info (id, location, email, number)
VALUES ('default', 'Curva Medellin, Cebu City, Philippines', 'presyohan@gmail.com', '+639 430 8387')
ON CONFLICT (id) DO NOTHING;

-- 2. Create app_ratings table
CREATE TABLE IF NOT EXISTS public.app_ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES public.app_users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for querying ratings
CREATE INDEX IF NOT EXISTS app_ratings_user_idx ON public.app_ratings(user_id);
CREATE INDEX IF NOT EXISTS app_ratings_created_idx ON public.app_ratings(created_at DESC);

-- 3. Create contact_messages table for threading
CREATE TABLE IF NOT EXISTS public.contact_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    parent_id UUID REFERENCES public.contact_messages(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for searching message tree
CREATE INDEX IF NOT EXISTS contact_messages_parent_idx ON public.contact_messages(parent_id);
CREATE INDEX IF NOT EXISTS contact_messages_created_idx ON public.contact_messages(created_at ASC);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.app_contact_info ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_ratings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_messages ENABLE ROW LEVEL SECURITY;

-- 5. Define policies for app_contact_info
DROP POLICY IF EXISTS select_contact_info ON public.app_contact_info;
CREATE POLICY select_contact_info ON public.app_contact_info
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS admin_all_contact_info ON public.app_contact_info;
CREATE POLICY admin_all_contact_info ON public.app_contact_info
    FOR ALL TO authenticated
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

-- 6. Define policies for app_ratings
DROP POLICY IF EXISTS select_ratings ON public.app_ratings;
CREATE POLICY select_ratings ON public.app_ratings
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS insert_rating ON public.app_ratings;
CREATE POLICY insert_rating ON public.app_ratings
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS update_rating ON public.app_ratings;
CREATE POLICY update_rating ON public.app_ratings
    FOR UPDATE TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS delete_rating ON public.app_ratings;
CREATE POLICY delete_rating ON public.app_ratings
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

-- 7. Define policies for contact_messages
DROP POLICY IF EXISTS select_messages ON public.contact_messages;
CREATE POLICY select_messages ON public.contact_messages
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS insert_message ON public.contact_messages;
CREATE POLICY insert_message ON public.contact_messages
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS update_message ON public.contact_messages;
CREATE POLICY update_message ON public.contact_messages
    FOR UPDATE TO authenticated
    USING (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'))
    WITH CHECK (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

DROP POLICY IF EXISTS delete_message ON public.contact_messages;
CREATE POLICY delete_message ON public.contact_messages
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

-- 8. Add updated_at trigger attachments
DROP TRIGGER IF EXISTS app_contact_info_updated_at ON public.app_contact_info;
CREATE TRIGGER app_contact_info_updated_at
BEFORE UPDATE ON public.app_contact_info
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS app_ratings_updated_at ON public.app_ratings;
CREATE TRIGGER app_ratings_updated_at
BEFORE UPDATE ON public.app_ratings
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP TRIGGER IF EXISTS contact_messages_updated_at ON public.contact_messages;
CREATE TRIGGER contact_messages_updated_at
BEFORE UPDATE ON public.contact_messages
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
