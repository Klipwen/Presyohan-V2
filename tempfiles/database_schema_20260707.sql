-- Database Schema Backup / Duplicate
-- Created on: 2026-07-07

-- Table stores
CREATE TABLE public.stores (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  branch text,
  type text,
  invite_code text UNIQUE,
  invite_code_created_at timestamp without time zone,
  created_at timestamp without time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  paste_code text,
  paste_code_expires_at timestamp with time zone,
  is_public boolean NOT NULL DEFAULT false,
  display_id text UNIQUE,
  is_standard_store boolean NOT NULL DEFAULT false,
  CONSTRAINT stores_pkey PRIMARY KEY (id)
);

-- Table products
CREATE TABLE public.products (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL,
  category_id uuid,
  name text NOT NULL,
  description text,
  price numeric NOT NULL,
  unit text,
  created_at timestamp without time zone NOT NULL DEFAULT now(),
  updated_at timestamp without time zone NOT NULL DEFAULT now(),
  is_public boolean NOT NULL DEFAULT false,
  CONSTRAINT products_pkey PRIMARY KEY (id),
  CONSTRAINT products_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id),
  CONSTRAINT products_category_fk FOREIGN KEY (category_id) REFERENCES public.categories(id)
);

-- Table app_users
CREATE TABLE public.app_users (
  id uuid NOT NULL,
  email text,
  name text,
  avatar_url text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  phone text,
  phone_normalized text,
  user_code text DEFAULT generate_user_code() UNIQUE,
  role text NOT NULL DEFAULT 'user'::text,
  last_activity_at timestamp with time zone NOT NULL DEFAULT now(),
  is_suspended boolean NOT NULL DEFAULT false,
  CONSTRAINT app_users_pkey PRIMARY KEY (id),
  CONSTRAINT app_users_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id)
);

-- Table categories
CREATE TABLE public.categories (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  store_id uuid NOT NULL,
  name text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT categories_pkey PRIMARY KEY (id),
  CONSTRAINT categories_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id)
);

-- Table store_members
CREATE TABLE public.store_members (
  store_id uuid NOT NULL,
  user_id uuid NOT NULL,
  role text NOT NULL DEFAULT 'member'::text,
  joined_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT store_members_pkey PRIMARY KEY (store_id, user_id),
  CONSTRAINT store_members_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id),
  CONSTRAINT store_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_users(id)
);

-- Table notifications
CREATE TABLE public.notifications (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  receiver_user_id uuid NOT NULL,
  sender_user_id uuid,
  store_id uuid,
  type text,
  title text,
  message text,
  read boolean NOT NULL DEFAULT false,
  read_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT notifications_pkey PRIMARY KEY (id),
  CONSTRAINT notifications_receiver_user_id_fkey FOREIGN KEY (receiver_user_id) REFERENCES public.app_users(id),
  CONSTRAINT notifications_sender_user_id_fkey FOREIGN KEY (sender_user_id) REFERENCES public.app_users(id),
  CONSTRAINT notifications_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id)
);

-- Table suki_relationships
CREATE TABLE public.suki_relationships (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  store_id uuid NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'active'::text,
  CONSTRAINT suki_relationships_pkey PRIMARY KEY (id),
  CONSTRAINT suki_relationships_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_users(id),
  CONSTRAINT suki_relationships_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.stores(id)
);

-- Table app_releases
CREATE TABLE public.app_releases (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  version_code integer NOT NULL UNIQUE,
  version_name text NOT NULL,
  download_url text NOT NULL,
  whats_new text NOT NULL,
  is_forced boolean NOT NULL DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  created_by uuid,
  CONSTRAINT app_releases_pkey PRIMARY KEY (id),
  CONSTRAINT app_releases_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.app_users(id)
);

-- Table announcements
CREATE TABLE public.announcements (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  title text NOT NULL,
  content text NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  created_by uuid,
  button_label text NOT NULL DEFAULT 'Close'::text,
  CONSTRAINT announcements_pkey PRIMARY KEY (id),
  CONSTRAINT announcements_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.app_users(id)
);
