-- Ensure categories are stored capitalized (UPPER) and avoid duplicates

create or replace function public.add_category(
  p_store_id uuid,
  p_name text
)
returns table(category_id uuid, store_id uuid, name text)
language plpgsql
as $$
declare
  v_trimmed text;
  v_upper text;
  v_existing_id uuid;
begin
  v_trimmed := trim(coalesce(p_name, ''));
  if v_trimmed = '' then
    raise exception 'Category name is required';
  end if;
  v_upper := upper(v_trimmed);

  -- Check for existing category case-insensitively
  select c.id into v_existing_id
  from public.categories c
  where c.store_id = p_store_id
    and lower(c.name) = lower(v_upper)
  limit 1;

  if v_existing_id is not null then
    return query
      select c.id, c.store_id, c.name
      from public.categories c
      where c.id = v_existing_id;
    return;
  end if;

  insert into public.categories (store_id, name)
  values (p_store_id, v_upper)
  returning id as category_id, store_id, name;
end;
$$;