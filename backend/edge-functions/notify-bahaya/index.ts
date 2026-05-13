/**
 * notify-bahaya — Edge Function Retak.id
 *
 * Trigger: Dipanggil saat ada INSERT di tabel laporan dengan status = 'BAHAYA'
 * Aksi: Kirim notifikasi ke admin via email/webhook
 */

interface LaporanPayload {
  record: {
    id: string;
    nama_lokasi: string;
    status: string;
    latitude: number;
    longitude: number;
    pelapor: string;
    created_at: string;
  };
}

Deno.serve(async (req: Request) => {
  try {
    const { record } = (await req.json()) as LaporanPayload;

    if (record.status !== 'BAHAYA') {
      return new Response(JSON.stringify({ skipped: true }), { status: 200 });
    }

    const message = `
⚠️ LAPORAN BAHAYA BARU
━━━━━━━━━━━━━━━━━━━
Lokasi: ${record.nama_lokasi}
Pelapor: ${record.pelapor}
Koordinat: ${record.latitude}, ${record.longitude}
Waktu: ${record.created_at}
━━━━━━━━━━━━━━━━━━━
Buka dashboard: ${Deno.env.get('DASHBOARD_URL') || 'https://retak.id'}/dashboard
    `.trim();

    // Kirim ke Telegram (jika bot token tersedia)
    const telegramToken = Deno.env.get('TELEGRAM_BOT_TOKEN');
    const telegramChatId = Deno.env.get('TELEGRAM_CHAT_ID');

    if (telegramToken && telegramChatId) {
      await fetch(`https://api.telegram.org/bot${telegramToken}/sendMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          chat_id: telegramChatId,
          text: message,
          parse_mode: 'HTML',
        }),
      });
    }

    // Kirim via email (Resend)
    const resendKey = Deno.env.get('RESEND_API_KEY');
    const adminEmail = Deno.env.get('ADMIN_EMAIL');

    if (resendKey && adminEmail) {
      await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${resendKey}`,
        },
        body: JSON.stringify({
          from: 'Retak.id <alert@retak.id>',
          to: adminEmail,
          subject: `[BAHAYA] Laporan baru di ${record.nama_lokasi}`,
          text: message,
        }),
      });
    }

    return new Response(JSON.stringify({ success: true }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
