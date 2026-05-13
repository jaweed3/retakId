/**
 * auto-cleanup — Edge Function Retak.id
 *
 * Trigger: Cron job (dijalankan setiap minggu)
 * Aksi: Arsipkan laporan >90 hari tanpa update
 */

Deno.serve(async (_req: Request) => {
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString();

    // Tandai laporan lama yang belum terverifikasi sebagai arsip
    const res = await fetch(
      `${supabaseUrl}/rest/v1/laporan?terverificado=eq.0&created_at=lt.${ninetyDaysAgo}&select=id`,
      { headers: { apikey: supabaseKey, Authorization: `Bearer ${supabaseKey}` } },
    );

    const oldReports = (await res.json()) as Array<{ id: string }>;

    if (oldReports.length > 0) {
      const ids = oldReports.map((r) => r.id);
      await fetch(`${supabaseUrl}/rest/v1/laporan?id=in.(${ids.join(',')})`, {
        method: 'PATCH',
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ is_archived: true }),
      });
    }

    return new Response(JSON.stringify({ success: true, archived: oldReports.length }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
