import logging
import math

from services.elevation import get_elevation

logger = logging.getLogger(__name__)

OFFSET_DEG = 0.0009
DEG_TO_M = 111_320.0


async def calculate_slope(
    lat: float,
    lon: float,
) -> float | None:
    center = await get_elevation(lat, lon, timeout=3.0)
    if center is None:
        return None

    points = [
        (lat + OFFSET_DEG, lon),
        (lat - OFFSET_DEG, lon),
        (lat, lon + OFFSET_DEG),
        (lat, lon - OFFSET_DEG),
    ]

    max_deg = 0.0
    successes = 0

    for pt_lat, pt_lon in points:
        elev = await get_elevation(pt_lat, pt_lon, timeout=3.0)
        if elev is None:
            continue

        successes += 1
        elev_diff = abs(elev - center)
        distance = OFFSET_DEG * DEG_TO_M
        slope_rad = math.atan(elev_diff / distance)
        slope_deg = math.degrees(slope_rad)

        if slope_deg > max_deg:
            max_deg = slope_deg

    if successes == 0:
        logger.warning("All 4 offset elevation fetches failed at (%.4f, %.4f)", lat, lon)
        return None

    logger.info("Slope at (%.4f, %.4f): %.1f° (%d points)", lat, lon, max_deg, successes)
    return round(max_deg, 1)
