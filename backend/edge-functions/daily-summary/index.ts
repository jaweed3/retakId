/**
 * daily-summary — Edge Function Retak.id
 *
 * Trigger: Cron job (dijalankan setiap hari jam 07:00 WIB)
 * Aksi: Hitung statistik harian, kirim ringkasan ke admin
 */

Deno.serve(async (_req: Request) => {
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    // Hitung laporan 24 jam terakhir
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

    const res = await fetch(
      `${supabaseUrl}/rest/v1/laporan?select=status,created_at&created_at=gte.${yesterday}`,
      { headers: { apikey: supabaseKey, Authorization: `Bearer ${supabaseKey}` } },
    );
    const reports = (await res.json()) as Array<{ status: string }>;

    const total = reports.length;
    const aman = reports.filter((r) => r.status === 'AMAN').length;
    const waspada = reports.filter((r) => r.status === 'WASPADA').length;
    const bahaya = reports.filter((r) => r.status === 'BAHAYA').length;

    const summary = `
📊 RINGKASAN HARIAN RETAK.ID
━━━━━━━━━━━━━━━━━━━━━━━━━
Tanggal: ${new Date().toLocaleDateString('id-ID')}
Total laporan baru: ${total}
  ✅ AMAN: ${aman}
  ⚠️ WASPADA: ${waspada}
  🔴 BAHAYA: ${bahaya}
━━━━━━━━━━━━━━━━━━━━━━━━━
    `.trim();

    // Kirim via email/telegram (sama seperti notify-bahaya)
    const telegramToken = Deno.env.get('TELEGRAM_BOT_TOKEN');
    const chatId = Deno.env.get('TELEGRAM_CHAT_ID');
    if (telegramToken && chatId) {
      await fetch(`https://api.telegram.org/bot${telegramToken}/sendMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ chat_id: chatId, text: summary }),
      });
    }

    return new Response(JSON.stringify({ success: true, total, aman, waspada, bahaya }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
