const CRAWLERS = [
  'facebookexternalhit',
  'Twitterbot',
  'LinkedInBot',
  'Slackbot',
  'Discordbot',
  'TelegramBot',
  'WhatsApp',
  'MetaInspector',
  'Applebot',
  'Googlebot',
  'bingbot',
  'Slurp',
  'DuckDuckBot',
  'Baiduspider',
  'YandexBot',
];

export default {
  async fetch(request, env, ctx) {
    const userAgent = request.headers.get('User-Agent') || '';
    const isCrawler = CRAWLERS.some(c => userAgent.includes(c));

    if (isCrawler) {
      const url = new URL(request.url);
      const originUrl = url.origin;

      const response = await fetch(request);
      const html = await response.text();

      const ogTitle = 'Retak.id — Pantau Retakan Tanah, Cegah Longsor Bersama';
      const ogDesc = 'Platform crowdsourcing deteksi dini retakan tanah di Jenangan, Ponorogo. Warga foto retakan lewat Android, AI deteksi tingkat bahaya.';
      const ogImage = `${originUrl}/og-image.jpg`;
      const ogUrl = originUrl;

      const seoHtml = html.replace(
        '</head>',
        `
    <meta property="og:title" content="${ogTitle}" />
    <meta property="og:description" content="${ogDesc}" />
    <meta property="og:image" content="${ogImage}" />
    <meta property="og:image:secure_url" content="${ogImage}" />
    <meta property="og:image:type" content="image/jpeg" />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
    <meta property="og:image:alt" content="${ogDesc}" />
    <meta property="og:url" content="${ogUrl}" />
    <meta property="og:type" content="website" />
    <meta property="og:site_name" content="Retak.id" />
    <meta property="og:locale" content="id_ID" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="${ogTitle}" />
    <meta name="twitter:description" content="${ogDesc}" />
    <meta name="twitter:image" content="${ogImage}" />
  </head>`
      );

      return new Response(seoHtml, {
        status: response.status,
        headers: {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'public, max-age=3600',
          'X-Robots-Tag': 'all',
        },
      });
    }

    return fetch(request);
  },
};
