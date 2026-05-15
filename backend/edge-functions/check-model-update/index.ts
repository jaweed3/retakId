/**
 * check-model-update — Edge Function Retak.id
 *
 * Trigger: Dipanggil oleh Android app untuk cek ketersediaan update model.
 * Aksi: Bandingkan versi yg terpasang di HP dengan versi terbaru di DB,
 *       return URL delta (.rkd) atau full model (.tflite).
 *
 * Request body:
 *   { current_version: string }
 *
 * Response:
 *   {
 *     update_available: boolean,
 *     latest_version: string,
 *     delta_url: string | null,
 *     full_url: string | null,
 *     changelog: string
 *   }
 */

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const STORAGE_BUCKET = "model-deltas";

interface CheckRequest {
  current_version: string;
}

Deno.serve(async (req: Request) => {
  try {
    // Validate env
    if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
      return new Response(
        JSON.stringify({ error: "Server configuration error" }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      );
    }

    // Parse request
    const { current_version }: CheckRequest = await req.json();
    if (!current_version) {
      return new Response(
        JSON.stringify({ error: "current_version is required" }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      );
    }

    // Query latest active version from DB
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);
    const { data: latest, error } = await supabase
      .from("model_versions")
      .select("version, delta_path, benchmark_accuracy, changelog")
      .eq("is_active", true)
      .order("created_at", { ascending: false })
      .limit(1)
      .single();

    if (error || !latest) {
      // No registered model — no update available
      return new Response(
        JSON.stringify({
          update_available: false,
          latest_version: current_version,
          delta_url: null,
          full_url: null,
          changelog: "",
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    // Check if current version is outdated
    const update_available = latest.version !== current_version;

    let delta_url: string | null = null;
    let full_url: string | null = null;

    if (update_available && latest.delta_path) {
      const { data: urlData } = supabase.storage
        .from(STORAGE_BUCKET)
        .getPublicUrl(latest.delta_path);
      delta_url = urlData.publicUrl;

      // Full model URL (fallback if delta apply fails)
      const fullPath = latest.delta_path.replace(/\.rkd$/i, ".tflite");
      const { data: fullUrlData } = supabase.storage
        .from(STORAGE_BUCKET)
        .getPublicUrl(fullPath);
      full_url = fullUrlData.publicUrl;
    }

    return new Response(
      JSON.stringify({
        update_available,
        latest_version: latest.version,
        delta_url,
        full_url,
        changelog: latest.changelog || "",
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(
      JSON.stringify({
        error: err instanceof Error ? err.message : "Unknown error",
      }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
