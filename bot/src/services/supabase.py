import json
import logging

import httpx

logger = logging.getLogger(__name__)


async def upload_photo(
    supabase_url: str,
    service_key: str,
    photo_bytes: bytes,
    user_id: int,
    timestamp: str,
) -> str | None:
    path = f"telegram/{user_id}/{timestamp}.jpg"
    url = f"{supabase_url}/storage/v1/object/laporan-foto/{path}"
    headers = {
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "image/jpeg",
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(url, headers=headers, content=photo_bytes)
            resp.raise_for_status()
            public_url = f"{supabase_url}/storage/v1/object/public/laporan-foto/{path}"
            logger.info("Photo uploaded: %s", public_url)
            return public_url
    except Exception as e:
        logger.warning("Photo upload failed: %s", e)
        return None


async def insert_report(
    supabase_url: str,
    service_key: str,
    data: dict,
) -> bool:
    url = f"{supabase_url}/rest/v1/laporan"
    headers = {
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url, headers=headers, json=data)
            resp.raise_for_status()
            logger.info("Report inserted for %s", data.get("pelapor", "?"))
            return True
    except Exception as e:
        logger.warning("Report insert failed: %s", e)
        return False


def build_report_data(
    lat: float,
    lon: float,
    pelapor: str,
    status: str,
    foto_url: str | None,
    catatan: dict,
) -> dict:
    return {
        "nama_lokasi": f"Telegram {pelapor} ({lat:.4f}, {lon:.4f})",
        "status": status,
        "catatan": json.dumps(catatan, ensure_ascii=False),
        "latitude": round(lat, 6),
        "longitude": round(lon, 6),
        "foto_url": foto_url or "",
        "pelapor": pelapor,
        "terverifikasi": 0,
    }
