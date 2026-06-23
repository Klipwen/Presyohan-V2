import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://sawwqpwnqilihlqiyurx.supabase.co';
const supabaseKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhd3dxcHducWlsaWhscWl5dXJ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE0Njc5NDUsImV4cCI6MjA3NzA0Mzk0NX0.H-XzrcPV0m80zncxr48cw2lFW104G-Ghos7zFEGfODg';

const supabase = createClient(supabaseUrl, supabaseKey);

async function run() {
  const { data, error } = await supabase
    .from('stores')
    .select('id, name, is_standard_store, is_public, is_default_standard');
  if (error) {
    console.error(error);
  } else {
    console.log('STORES IN DATABASE:', data);
  }
}
run();
