-- Safe, idempotent migration to grant RLS insert policies on public.notifications for authenticated administrator accounts.

DROP POLICY IF EXISTS admin_insert_notifications ON public.notifications;
CREATE POLICY admin_insert_notifications ON public.notifications
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.app_users
            WHERE id = auth.uid() AND role = 'admin'
        )
    );

DROP POLICY IF EXISTS admin_all_notifications ON public.notifications;
CREATE POLICY admin_all_notifications ON public.notifications
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.app_users
            WHERE id = auth.uid() AND role = 'admin'
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.app_users
            WHERE id = auth.uid() AND role = 'admin'
        )
    );
