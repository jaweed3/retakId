import logging

import httpx

logger = logging.getLogger(__name__)

OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

REQUIRED_FIELDS = [
    "temperature_2m",
    "relative_humidity_2m",
    "precipitation",
    "rain",
    "weather_code",
    "wind_speed_10m",
]


async def get_weather(
    lat: float,
    lon: float,
    timeout: float = 5.0,
) -> dict | None:
    params = {
        "latitude": lat,
        "longitude": lon,
        "current": ",".join(REQUIRED_FIELDS),
        "timezone": "Asia/Jakarta",
    }

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(OPEN_METEO_FORECAST_URL, params=params)
            resp.raise_for_status()
            data = resp.json()

            current = data.get("current")
            if not current:
                return None

            result = {
                "temperature": current.get("temperature_2m"),
                "humidity": current.get("relative_humidity_2m"),
                "precipitation": current.get("precipitation"),
                "rain": current.get("rain", 0.0) or 0.0,
                "weather_code": current.get("weather_code"),
                "wind_speed": current.get("wind_speed_10m"),
            }

            logger.info(
                "Weather at (%.4f, %.4f): rain=%.1fmm temp=%.1f°C",
                lat, lon, result["rain"], result["temperature"],
            )
            return result

    except Exception as e:
        logger.warning("Weather fetch failed for (%.4f, %.4f): %s", lat, lon, e)
        return None
