import { Helmet } from 'react-helmet-async';

interface SEOMetaProps {
  title: string;
  description?: string;
  image?: string;
  url?: string;
  noindex?: boolean;
}

const SITE_NAME = 'Retak.id';
const BASE_URL = 'https://retak.utc.web.id';
const DEFAULT_DESC = 'Platform crowdsourcing deteksi dini retakan tanah di Jenangan, Ponorogo. Warga foto retakan lewat Android, AI deteksi tingkat bahaya.';
const DEFAULT_IMAGE = '/og-image.png';

export function SEOMeta({
  title,
  description = DEFAULT_DESC,
  image = DEFAULT_IMAGE,
  url,
  noindex = false,
}: SEOMetaProps) {
  const fullTitle = title.includes(SITE_NAME) ? title : `${title} — ${SITE_NAME}`;
  const imageUrl = image.startsWith('http') ? image : `${BASE_URL}${image}`;

  return (
    <Helmet>
      <title>{fullTitle}</title>
      <meta name="description" content={description} />
      {noindex && <meta name="robots" content="noindex, nofollow" />}

      {/* Open Graph */}
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:image" content={imageUrl} />
      <meta property="og:image:width" content="1200" />
      <meta property="og:image:height" content="630" />
      <meta property="og:url" content={url || BASE_URL} />
      <meta property="og:type" content="website" />
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:locale" content="id_ID" />

      {/* Twitter */}
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={description} />
      <meta name="twitter:image" content={imageUrl} />
    </Helmet>
  );
}
