-- Safely delete a category: move its products to uncategorized (NULL) then delete

create or replace function public.delete_category_safe(
  p_store_id uuid,
  p_category_id uuid
)
returns void
language plpgsql
as $$
begin
  -- Reassign products to uncategorized
  update public.products
    set category_id = null
    where store_id = p_store_id
      and category_id = p_category_id;

  -- Delete category
  delete from public.categories
    where id = p_category_id
      and store_id = p_store_id;
end;
$$;