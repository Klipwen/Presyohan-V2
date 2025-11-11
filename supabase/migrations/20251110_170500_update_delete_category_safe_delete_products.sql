-- Update delete_category_safe to delete products in the category, then delete category

create or replace function public.delete_category_safe(
  p_store_id uuid,
  p_category_id uuid
)
returns void
language plpgsql
as $$
begin
  -- Delete all products in this category for the store
  delete from public.products
    where store_id = p_store_id
      and category_id = p_category_id;

  -- Delete the category itself
  delete from public.categories
    where id = p_category_id
      and store_id = p_store_id;
end;
$$;