// AgroMarket: Supabase Admin Client for Edge Functions
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.8";

export function getAdminClient() {
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

  return createClient(supabaseUrl, supabaseServiceKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });
}
