-- Migration: Remove Suki partnership when user joins the store as member/owner
-- Create a trigger function that automatically deletes the suki relationship
CREATE OR REPLACE FUNCTION public.remove_suki_on_member_insert()
RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM public.suki_relationships
  WHERE user_id = NEW.user_id AND store_id = NEW.store_id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create trigger on public.store_members
DROP TRIGGER IF EXISTS trg_remove_suki_on_member_insert ON public.store_members;
CREATE TRIGGER trg_remove_suki_on_member_insert
AFTER INSERT ON public.store_members
FOR EACH ROW
EXECUTE FUNCTION public.remove_suki_on_member_insert();
