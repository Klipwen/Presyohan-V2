-- RPC: update_store_member_role(store_id, member_id, new_role)
-- Promotes/demotes a member''s role with security checks and notifications.
-- Rules:
-- - Only owners or managers can change roles
-- - Assigning owner requires the actor to be an owner
-- - Target must be a current member

BEGIN;

CREATE OR REPLACE FUNCTION public.update_store_member_role(
    p_store_id uuid,
    p_member_id uuid,
    p_new_role text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_actor_id uuid;
  v_actor_role text;
  v_target_role text;
  v_store_name text;
  v_actor_name text;
BEGIN
  -- Validate role input
  IF p_new_role NOT IN ('employee','manager','owner') THEN
    RAISE EXCEPTION 'Invalid role: %', p_new_role;
  END IF;

  SELECT auth.uid() INTO v_actor_id;

  -- Ensure actor has privileges in this store
  SELECT role INTO v_actor_role
  FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_actor_id;

  IF v_actor_role IS NULL OR v_actor_role NOT IN ('owner','manager') THEN
    RAISE EXCEPTION 'Insufficient privileges to change roles.';
  END IF;

  -- Fetch target''s current role; ensure membership exists
  SELECT role INTO v_target_role
  FROM public.store_members
  WHERE store_id = p_store_id AND user_id = p_member_id;

  IF v_target_role IS NULL THEN
    RAISE EXCEPTION 'Target user is not a member of this store.';
  END IF;

  -- Assigning owner requires an owner actor
  IF p_new_role = 'owner' AND v_actor_role <> 'owner' THEN
    RAISE EXCEPTION 'Only owners can assign the owner role.';
  END IF;

  -- Perform role change
  UPDATE public.store_members
  SET role = p_new_role
  WHERE store_id = p_store_id AND user_id = p_member_id;

  -- Prepare names for notification context
  SELECT s.name INTO v_store_name FROM public.stores s WHERE s.id = p_store_id;
  SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_actor_name FROM public.app_users u WHERE u.id = v_actor_id;

  -- Notify target about role change
  INSERT INTO public.notifications (
    receiver_user_id,
    sender_user_id,
    store_id,
    type,
    message,
    created_at
  ) VALUES (
    p_member_id,
    v_actor_id,
    p_store_id,
    'role_change',
    format('%s changed your role to %s in %s', v_actor_name, p_new_role, COALESCE(v_store_name, 'store')),
    now()
  );
END;
$$;

COMMENT ON FUNCTION public.update_store_member_role(uuid, uuid, text)
IS 'Change a store member''s role with security checks and notify the user.';

GRANT EXECUTE ON FUNCTION public.update_store_member_role(uuid, uuid, text) TO authenticated;

COMMIT;