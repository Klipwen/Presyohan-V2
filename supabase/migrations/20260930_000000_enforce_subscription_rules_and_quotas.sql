-- Migration: Enforce Subscription Store Quota, Role Cap (Option 1), and Owner Departure Downgrades
-- Date: September 30, 2026

BEGIN;

-- 0. Ensure is_public_preference column exists on stores to remember desired visibility
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'stores' AND column_name = 'is_public_preference'
    ) THEN
        ALTER TABLE public.stores ADD COLUMN is_public_preference BOOLEAN NOT NULL DEFAULT false;
        UPDATE public.stores SET is_public_preference = is_public;
    END IF;
END $$;


-- 1. Function to sync stores' tier when an app_user's tier is updated (restores is_public_preference on upgrade)
CREATE OR REPLACE FUNCTION public.sync_user_stores_tier()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_allow_customer_pairing BOOLEAN;
BEGIN
    -- If subscription_tier or subscription_expires_at has changed
    IF (OLD.subscription_tier IS DISTINCT FROM NEW.subscription_tier) OR 
       (OLD.subscription_expires_at IS DISTINCT FROM NEW.subscription_expires_at) THEN
        
        -- Dynamically check if the new tier allows customer pairing from subscription_tiers
        SELECT COALESCE(has_customer_pairing, false) INTO v_allow_customer_pairing
        FROM public.subscription_tiers
        WHERE tier_id = LOWER(COALESCE(NEW.subscription_tier, 'free'));

        -- Update stores where this user is the billing owner or an owner
        UPDATE public.stores
        SET 
            subscription_tier = LOWER(NEW.subscription_tier),
            subscription_expires_at = NEW.subscription_expires_at,
            billing_owner_id = COALESCE(billing_owner_id, NEW.id),
            is_public = CASE 
                WHEN v_allow_customer_pairing = false THEN false 
                ELSE is_public_preference 
            END,
            updated_at = NOW()
        WHERE billing_owner_id = NEW.id
           OR id IN (
               SELECT store_id FROM public.store_members
               WHERE user_id = NEW.id AND role = 'owner'
           );
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_sync_user_stores_tier ON public.app_users;
CREATE TRIGGER trigger_sync_user_stores_tier
AFTER UPDATE OF subscription_tier, subscription_expires_at ON public.app_users
FOR EACH ROW
EXECUTE FUNCTION public.sync_user_stores_tier();


-- 1.1 Trigger on stores to guarantee store public status dynamically follows subscription_tiers admin configuration and preserves preference
CREATE OR REPLACE FUNCTION public.enforce_store_tier_rules()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_allow_customer_pairing BOOLEAN;
BEGIN
    -- Standard / Presyohan comparator stores can remain public
    IF NEW.is_standard_store = true THEN
        RETURN NEW;
    END IF;

    -- If is_public was explicitly changed by user action, update is_public_preference to match
    IF (TG_OP = 'INSERT') OR (OLD.is_public IS DISTINCT FROM NEW.is_public) THEN
        IF NEW.is_public = true THEN
            NEW.is_public_preference := true;
        ELSIF (TG_OP = 'UPDATE' AND OLD.subscription_tier = NEW.subscription_tier AND OLD.is_public = true AND NEW.is_public = false) THEN
            NEW.is_public_preference := false;
        END IF;
    END IF;

    -- Dynamically check if this store's subscription tier allows customer pairing / public mode
    SELECT COALESCE(has_customer_pairing, false) INTO v_allow_customer_pairing
    FROM public.subscription_tiers
    WHERE tier_id = LOWER(COALESCE(NEW.subscription_tier, 'free'));

    -- If customer pairing is disabled by admin for this tier, force is_public = false (preference remains saved!)
    IF v_allow_customer_pairing = false THEN
        NEW.is_public := false;
    ELSE
        -- If customer pairing is enabled, ensure is_public restores to preference
        IF NEW.is_public_preference = true THEN
            NEW.is_public := true;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_enforce_store_tier_rules ON public.stores;
CREATE TRIGGER trigger_enforce_store_tier_rules
BEFORE INSERT OR UPDATE OF subscription_tier, is_public, is_standard_store, is_public_preference ON public.stores
FOR EACH ROW
EXECUTE FUNCTION public.enforce_store_tier_rules();


-- 1.2 Trigger on subscription_tiers to automatically sync stores when Admin toggles has_customer_pairing on dashboard
CREATE OR REPLACE FUNCTION public.sync_tier_feature_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF OLD.has_customer_pairing IS DISTINCT FROM NEW.has_customer_pairing THEN
        IF NEW.has_customer_pairing = true THEN
            -- Restore public status for stores whose preference was public
            UPDATE public.stores
            SET 
                is_public = is_public_preference,
                updated_at = NOW()
            WHERE LOWER(COALESCE(subscription_tier, 'free')) = LOWER(NEW.tier_id)
              AND is_standard_store = false;
        ELSE
            -- Demote all stores of this tier to private, keeping preference intact
            UPDATE public.stores
            SET 
                is_public = false,
                updated_at = NOW()
            WHERE LOWER(COALESCE(subscription_tier, 'free')) = LOWER(NEW.tier_id)
              AND is_standard_store = false;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_sync_tier_feature_changes ON public.subscription_tiers;
CREATE TRIGGER trigger_sync_tier_feature_changes
AFTER UPDATE OF has_customer_pairing ON public.subscription_tiers
FOR EACH ROW
EXECUTE FUNCTION public.sync_tier_feature_changes();


-- 1.3 Retroactive Dynamic Cleanup: Sync all existing stores based on subscription_tiers configuration
UPDATE public.stores s
SET is_public = false
FROM public.subscription_tiers st
WHERE s.is_standard_store = false 
  AND LOWER(COALESCE(s.subscription_tier, 'free')) = st.tier_id
  AND st.has_customer_pairing = false;


-- 2. Enhanced create_store RPC with store quota enforcement
CREATE OR REPLACE FUNCTION public.create_store(p_name TEXT, p_branch TEXT, p_type TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_store_id UUID;
  v_user_id UUID;
  v_user_tier TEXT;
  v_max_stores INT;
  v_current_owned_count INT;
  has_auth_uid BOOLEAN;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Store name is required';
  END IF;

  -- Detect app_users identity model and upsert profile accordingly
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  IF has_auth_uid THEN
    INSERT INTO public.app_users (auth_uid, email, created_at)
    VALUES (auth.uid(), NULL, NOW())
    ON CONFLICT (auth_uid) DO NOTHING;
    SELECT id INTO v_user_id FROM public.app_users WHERE auth_uid = auth.uid();
  ELSE
    INSERT INTO public.app_users (id, email, created_at)
    VALUES (auth.uid(), NULL, NOW())
    ON CONFLICT (id) DO NOTHING;
    SELECT id INTO v_user_id FROM public.app_users WHERE id = auth.uid();
  END IF;

  -- 1. Fetch user's subscription tier
  SELECT COALESCE(subscription_tier, 'free') INTO v_user_tier
  FROM public.app_users
  WHERE id = v_user_id;

  -- 2. Fetch max_stores allowed from subscription_tiers
  SELECT COALESCE(max_stores, 1) INTO v_max_stores
  FROM public.subscription_tiers
  WHERE tier_id = LOWER(COALESCE(v_user_tier, 'free'));

  IF v_max_stores IS NULL THEN
    v_max_stores := 1;
  END IF;

  -- 3. Count current stores where the user holds the 'owner' role
  SELECT COUNT(DISTINCT store_id) INTO v_current_owned_count
  FROM public.store_members
  WHERE user_id = v_user_id AND role = 'owner';

  -- Enforce store quota limit
  IF v_current_owned_count >= v_max_stores THEN
    RAISE EXCEPTION 'Store limit reached. Your current plan allows up to % store(s). Please upgrade to create more stores.', v_max_stores;
  END IF;

  -- Create store with billing_owner_id and subscription_tier
  INSERT INTO public.stores (name, branch, type, billing_owner_id, subscription_tier)
  VALUES (
    trim(p_name), 
    NULLIF(trim(p_branch), ''), 
    NULLIF(trim(p_type), ''),
    v_user_id,
    LOWER(COALESCE(v_user_tier, 'free'))
  )
  RETURNING id INTO v_store_id;

  -- Assign caller as owner using resolved app_users.id
  INSERT INTO public.store_members (store_id, user_id, role)
  VALUES (v_store_id, v_user_id, 'owner');

  RETURN v_store_id;
END;
$$;

COMMENT ON FUNCTION public.create_store(TEXT, TEXT, TEXT)
IS 'Creates a store with quota enforcement and assigns the caller as owner; returns store id.';

GRANT EXECUTE ON FUNCTION public.create_store(TEXT, TEXT, TEXT) TO authenticated;
ALTER FUNCTION public.create_store(TEXT, TEXT, TEXT) OWNER TO postgres;


-- 3. Enhanced update_store_member_role RPC with Role Cap (Option 1)
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
  v_target_tier text;
  v_max_stores int;
  v_target_owned_count int;
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

  -- Fetch target's current role; ensure membership exists
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

  -- If promoting to owner, check target user's store ownership capacity (Option 1 Role Cap)
  IF p_new_role = 'owner' AND v_target_role <> 'owner' THEN
    -- Fetch target user's active tier
    SELECT COALESCE(subscription_tier, 'free') INTO v_target_tier
    FROM public.app_users
    WHERE id = p_member_id;

    -- Fetch target user's max_stores limit
    SELECT COALESCE(max_stores, 1) INTO v_max_stores
    FROM public.subscription_tiers
    WHERE tier_id = LOWER(COALESCE(v_target_tier, 'free'));

    IF v_max_stores IS NULL THEN
      v_max_stores := 1;
    END IF;

    -- Count existing stores where target user is an owner (excluding this store)
    SELECT COUNT(DISTINCT store_id) INTO v_target_owned_count
    FROM public.store_members
    WHERE user_id = p_member_id AND role = 'owner' AND store_id <> p_store_id;

    IF (v_target_owned_count + 1) > v_max_stores THEN
      RAISE EXCEPTION 'This member is on the Free Tier and can only own 1 store. You can promote them to Manager instead, or they can upgrade their subscription plan.';
    END IF;
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
IS 'Change a store member role with quota validation and notify the user.';

GRANT EXECUTE ON FUNCTION public.update_store_member_role(uuid, uuid, text) TO authenticated;
ALTER FUNCTION public.update_store_member_role(uuid, uuid, text) OWNER TO postgres;


-- 4. Enhanced leave_store RPC with Owner Departure & Billing Transfer
CREATE OR REPLACE FUNCTION public.leave_store(p_store_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_user_id UUID;
  v_role TEXT;
  owner_count INTEGER;
  v_store_name TEXT;
  v_user_email TEXT;
  v_user_name TEXT;
  has_auth_uid BOOLEAN;
  v_new_billing_owner_id UUID;
  v_new_billing_owner_tier TEXT;
  v_new_billing_owner_expires TIMESTAMPTZ;
  v_allow_customer_pairing BOOLEAN;
  m RECORD;
BEGIN
  SELECT auth.uid() INTO v_user_id;

  SELECT role INTO v_role
  FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  IF v_role IS NULL THEN
    RETURN FALSE;
  END IF;

  IF v_role = 'owner' THEN
    SELECT COUNT(*) INTO owner_count
    FROM public.store_members
    WHERE store_id = p_store_id AND role = 'owner';

    IF owner_count <= 1 THEN
      RAISE EXCEPTION 'Cannot leave as sole owner. Transfer ownership first.' USING ERRCODE = 'insufficient_privilege';
    END IF;
  END IF;

  SELECT name INTO v_store_name FROM public.stores WHERE id = p_store_id;
  SELECT email INTO v_user_email FROM auth.users WHERE id = v_user_id;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'auth_uid'
  ) INTO has_auth_uid;

  IF has_auth_uid THEN
    SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_user_name
    FROM public.app_users u
    WHERE u.auth_uid = v_user_id
    LIMIT 1;
  ELSE
    SELECT COALESCE(u.name, split_part(u.email, '@', 1)) INTO v_user_name
    FROM public.app_users u
    WHERE u.id = v_user_id
    LIMIT 1;
  END IF;

  -- Delete membership
  DELETE FROM public.store_members
  WHERE store_id = p_store_id AND user_id = v_user_id;

  -- If departing user was an owner, reassign billing_owner_id to a remaining owner
  IF v_role = 'owner' THEN
    SELECT user_id INTO v_new_billing_owner_id
    FROM public.store_members
    WHERE store_id = p_store_id AND role = 'owner'
    LIMIT 1;

    IF v_new_billing_owner_id IS NOT NULL THEN
      SELECT 
        COALESCE(subscription_tier, 'free'),
        subscription_expires_at
      INTO 
        v_new_billing_owner_tier,
        v_new_billing_owner_expires
      FROM public.app_users
      WHERE id = v_new_billing_owner_id;

      -- Dynamically check if the new billing owner's tier allows customer pairing
      SELECT COALESCE(has_customer_pairing, false) INTO v_allow_customer_pairing
      FROM public.subscription_tiers
      WHERE tier_id = LOWER(COALESCE(v_new_billing_owner_tier, 'free'));

      UPDATE public.stores
      SET 
        billing_owner_id = v_new_billing_owner_id,
        subscription_tier = LOWER(COALESCE(v_new_billing_owner_tier, 'free')),
        subscription_expires_at = v_new_billing_owner_expires,
        is_public = CASE 
          WHEN v_allow_customer_pairing = false THEN false 
          ELSE is_public_preference 
        END,
        updated_at = NOW()
      WHERE id = p_store_id;
    END IF;
  END IF;

  -- Broadcast to remaining members
  FOR m IN SELECT user_id FROM public.store_members WHERE store_id = p_store_id LOOP
    INSERT INTO public.notifications (
      receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
    ) VALUES (
      m.user_id,
      v_user_id,
      p_store_id,
      'member_left',
      'Member Left',
      COALESCE(v_user_name, split_part(COALESCE(v_user_email,''),'@',1), 'A user') || ' left ' || v_store_name,
      false,
      now()
    );
  END LOOP;

  -- Self notification
  INSERT INTO public.notifications (
    receiver_user_id, sender_user_id, store_id, type, title, message, read, created_at
  ) VALUES (
    v_user_id,
    v_user_id,
    p_store_id,
    'leave_confirmed',
    'Left Store',
    'You left ' || v_store_name,
    false,
    now()
  );

  RETURN TRUE;
END;
$$;

COMMENT ON FUNCTION public.leave_store(UUID)
IS 'Leaves a store and automatically reassigns billing ownership and store tier to a remaining owner if necessary.';

GRANT EXECUTE ON FUNCTION public.leave_store(UUID) TO authenticated;
ALTER FUNCTION public.leave_store(UUID) OWNER TO postgres;

COMMIT;
