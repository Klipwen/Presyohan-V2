import { createClient } from '@supabase/supabase-js'

const supabaseUrl = "https://sawwqpwnqilihlqiyurx.supabase.co"
const supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhd3dxcHducWlsaWhscWl5dXJ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE0Njc5NDUsImV4cCI6MjA3NzA0Mzk0NX0.H-XzrcPV0m80zncxr48cw2lFW104G-Ghos7zFEGfODg"

export const supabase = createClient(supabaseUrl, supabaseAnonKey)