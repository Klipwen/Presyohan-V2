-- Supabase schema and RLS for Presyohan

-- Enable required extensions
create extension if not exists "uuid-ossp";
create extension if not exists pgcrypto;

-- Users mapped to auth.users via uid
create table if not exists app_users (
  id uuid primary key default gen_random_uuid(),
  auth_uid uuid not null unique,
  email text not null,
  name text,
  created_at timestamptz not null default now()
);

create unique index if not exists app_users_auth_uid_idx on app_users(auth_uid);

-- Stores
create table if not exists stores (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references app_users(id) on delete cascade,
  name text not null,
  branch text,
  created_at timestamptz not null default now()
);

create index if not exists stores_owner_id_idx on stores(owner_id);

-- Store members
create table if not exists store_members (
  store_id uuid not null references stores(id) on delete cascade,
  user_id uuid not null references app_users(id) on delete cascade,
  role text not null check (role in ('owner','manager','staff')),
  created_at timestamptz not null default now(),
  primary key (store_id, user_id)
);

create index if not exists store_members_user_idx on store_members(user_id);

-- Categories
create table if not exists categories (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references stores(id) on delete cascade,
  name text not null,
  created_at timestamptz not null default now(),
  unique (store_id, name)
);

create index if not exists categories_store_idx on categories(store_id);

-- Products
create table if not exists products (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references stores(id) on delete cascade,
  name text not null,
  description text,
  price numeric(12,2) not null default 0,
  units text,
  category text,
  created_at timestamptz not null default now()
);

create index if not exists products_store_idx on products(store_id);
create index if not exists products_category_idx on products(store_id, category);

-- Notifications (flattened at user level)
create table if not exists notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references app_users(id) on delete cascade,
  sender_id uuid references app_users(id) on delete set null,
  title text,
  message text,
  status text default 'Pending', -- Pending, Accepted, Rejected
  unread boolean default true,
  store_id uuid references stores(id) on delete set null,
  role text,
  created_at timestamptz not null default now()
);

create index if not exists notifications_user_idx on notifications(user_id);

-- RLS enable
alter table app_users enable row level security;
alter table stores enable row level security;
alter table store_members enable row level security;
alter table categories enable row level security;
alter table products enable row level security;
alter table notifications enable row level security;

-- Helper function to get app_user.id from auth.uid
create or replace function get_app_user_id()
returns uuid language sql stable security definer set search_path = public as $$
  select id from app_users where auth_uid = auth.uid()
$$;

-- Seed app_users on signup
create or replace function handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into app_users(auth_uid, email, name)
  values (new.id, new.email, new.raw_user_meta_data->>'name')
  on conflict (auth_uid) do nothing;
  return new;
end;
$$;

-- NOTE: Attach to auth.users via a trigger in the Supabase dashboard or SQL:
-- create trigger on_auth_user_created after insert on auth.users
-- for each row execute function handle_new_user();

-- Policies
-- app_users: users can view/update their own profile
create policy app_users_select_self on app_users
  for select using (auth_uid = auth.uid());
create policy app_users_update_self on app_users
  for update using (auth_uid = auth.uid());

-- stores: owner and members can select; insert restricted to authenticated; update/delete owner/manager
create policy stores_select_members on stores
  for select using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = stores.id and u.auth_uid = auth.uid()
    ) or
    exists(
      select 1 from app_users u where u.id = stores.owner_id and u.auth_uid = auth.uid()
    )
  );
create policy stores_insert_auth on stores
  for insert with check (
    exists(select 1 from app_users u where u.auth_uid = auth.uid())
  );
create policy stores_update_owner_manager on stores
  for update using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = stores.id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  );
create policy stores_delete_owner on stores
  for delete using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = stores.id and u.auth_uid = auth.uid() and m.role = 'owner'
    )
  );

-- store_members: user can see memberships they belong to; insert/update by owner/manager
create policy store_members_select_self on store_members
  for select using (
    exists(
      select 1 from app_users u where u.id = store_members.user_id and u.auth_uid = auth.uid()
    )
  );
create policy store_members_insert_by_manager on store_members
  for insert with check (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = store_members.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  );
create policy store_members_update_by_manager on store_members
  for update using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = store_members.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  );
create policy store_members_delete_by_owner on store_members
  for delete using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = store_members.store_id and u.auth_uid = auth.uid() and m.role = 'owner'
    )
  );

-- categories: readable by members; insert/update/delete by manager/owner
create policy categories_select_members on categories
  for select using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = categories.store_id and u.auth_uid = auth.uid()
    )
  );
create policy categories_cud_by_manager on categories
  for all using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = categories.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  ) with check (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = categories.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  );

-- products: readable by members; insert/update/delete by manager/owner
create policy products_select_members on products
  for select using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = products.store_id and u.auth_uid = auth.uid()
    )
  );
create policy products_cud_by_manager on products
  for all using (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = products.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  ) with check (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where m.store_id = products.store_id and u.auth_uid = auth.uid() and m.role in ('owner','manager')
    )
  );

-- notifications: users read their own; sender (manager/owner) can insert for recipients; update unread
create policy notifications_select_self on notifications
  for select using (
    exists(
      select 1 from app_users u where u.id = notifications.user_id and u.auth_uid = auth.uid()
    )
  );
create policy notifications_insert_sender on notifications
  for insert with check (
    exists(
      select 1 from store_members m
      join app_users u on u.id = m.user_id
      where (notifications.store_id is null or m.store_id = notifications.store_id)
        and u.auth_uid = auth.uid()
        and m.role in ('owner','manager')
    )
  );
create policy notifications_update_self on notifications
  for update using (
    exists(
      select 1 from app_users u where u.id = notifications.user_id and u.auth_uid = auth.uid()
    )
  );