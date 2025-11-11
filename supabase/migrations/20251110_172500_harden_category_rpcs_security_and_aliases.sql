-- Ensure category management RPCs use aliases and SECURITY DEFINER

create or replace function public.rename_or_merge_category(
  p_store_id uuid,
  p_category_id uuid,
  p_new_name text
)
returns table(category_id uuid, store_id uuid, name text)
language plpgsql
security definer set search_path = public
as $$
declare
  v_trimmed text;
  v_upper text;
  v_existing_id uuid;
begin
  v_trimmed := trim(coalesce(p_new_name, ''));
  if v_trimmed = '' then
    raise exception 'Category name is required';
  end if;
  v_upper := upper(v_trimmed);

  select c.id into v_existing_id
  from public.categories c
  where c.store_id = p_store_id
    and lower(c.name) = lower(v_upper)
  limit 1;

  if v_existing_id is not null and v_existing_id <> p_category_id then
    update public.products p
      set category_id = v_existing_id
      where p.store_id = p_store_id
        and p.category_id = p_category_id;

    delete from public.categories c
      where c.id = p_category_id
        and c.store_id = p_store_id;

    return query
      select c.id, c.store_id, c.name
      from public.categories c
      where c.id = v_existing_id;
    return;
  end if;

  update public.categories c
    set name = v_upper
    where c.id = p_category_id
      and c.store_id = p_store_id;

  return query
    select c.id, c.store_id, c.name
    from public.categories c
    where c.id = p_category_id;
end;
$$;

create or replace function public.delete_category_safe(
  p_store_id uuid,
  p_category_id uuid
)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
  delete from public.products p
    where p.store_id = p_store_id
      and p.category_id = p_category_id;

  delete from public.categories c
    where c.id = p_category_id
      and c.store_id = p_store_id;
end;
$$;

grant execute on function public.rename_or_merge_category(uuid, uuid, text) to authenticated;
grant execute on function public.delete_category_safe(uuid, uuid) to authenticated;