import logging

import httpx

logger = logging.getLogger(__name__)

OPEN_METEO_ELEVATION_URL = "https://api.open-meteo.com/v1/elevation"


async def get_elevation(lat: float, lon: float, timeout: float = 5.0) -> float | None:
    params = {"latitude": lat, "longitude": lon}

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(OPEN_METEO_ELEVATION_URL, params=params)
            resp.raise_for_status()
            data = resp.json()

            elevation = data["elevation"][0]
            logger.info("Elevation at (%.4f, %.4f): %.1f m", lat, lon, elevation)
            return float(elevation)

    except Exception as e:
        logger.warning("Elevation fetch failed for (%.4f, %.4f): %s", lat, lon, e)
        return None
