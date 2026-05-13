/**
 * moderate-spam — Edge Function Retak.id
 *
 * Trigger: Dipanggil saat INSERT di tabel laporan
 * Aksi: Deteksi spam — jika pelapor sama submit >5 laporan dalam 1 menit
 */

interface SpamPayload {
  record: {
    id: string;
    pelapor: string;
    created_at: string;
  };
}

Deno.serve(async (req: Request) => {
  try {
    const { record } = (await req.json()) as SpamPayload;
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const oneMinuteAgo = new Date(Date.now() - 60 * 1000).toISOString();

    // Hitung berapa laporan dari pelapor yang sama dalam 1 menit terakhir
    const res = await fetch(
      `${supabaseUrl}/rest/v1/laporan?pelapor=eq.${encodeURIComponent(record.pelapor)}&created_at=gte.${oneMinuteAgo}&select=id`,
      {
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`,
        },
      },
    );

    const recent = (await res.json()) as Array<{ id: string }>;

    if (recent.length > 5) {
      // Flag as spam
      await fetch(`${supabaseUrl}/rest/v1/laporan?id=eq.${record.id}`, {
        method: 'PATCH',
        headers: {
          apikey: supabaseKey,
          Authorization: `Bearer ${supabaseKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ terverifikasi: -1, catatan: '[SPAM] Terdeteksi otomatis' }),
      });
    }

    return new Response(JSON.stringify({ success: true, flagged: recent.length > 5 }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
