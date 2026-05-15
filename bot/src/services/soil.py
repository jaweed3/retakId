import logging

import httpx

logger = logging.getLogger(__name__)

ISRIC_SOILGRIDS_URL = "https://rest.isric.org/soilgrids/v2.0/classification/query"

WRB_RISK_MAP = {
    "Acrisol": 0.7,
    "Albeluvisol": 0.5,
    "Alisol": 0.7,
    "Andosol": 0.3,
    "Arenosol": 0.6,
    "Cambisol": 0.4,
    "Chernozem": 0.2,
    "Ferralsol": 0.5,
    "Fluvisol": 0.8,
    "Gleysol": 0.9,
    "Histosol": 0.9,
    "Kastanozem": 0.3,
    "Leptosol": 0.7,
    "Luvisol": 0.4,
    "Nitisol": 0.3,
    "Phaeozem": 0.3,
    "Planosol": 0.7,
    "Plinthosol": 0.6,
    "Podzol": 0.5,
    "Regosol": 0.6,
    "Solonchak": 0.8,
    "Solonetz": 0.7,
    "Umbrisol": 0.4,
    "Vertisol": 0.9,
}

WRB_NAME_MAP = {
    "Acrisol": "Acrisol (tanah masam)",
    "Albeluvisol": "Albeluvisol",
    "Alisol": "Alisol (tanah masam)",
    "Andosol": "Andosol (tanah vulkanik)",
    "Arenosol": "Arenosol (tanah berpasir)",
    "Cambisol": "Cambisol (tanah muda)",
    "Chernozem": "Chernozem (tanah hitam)",
    "Ferralsol": "Ferralsol (tanah merah)",
    "Fluvisol": "Fluvisol (tanah endapan)",
    "Gleysol": "Gleysol (tanah jenuh air)",
    "Histosol": "Histosol (tanah gambut)",
    "Kastanozem": "Kastanozem",
    "Leptosol": "Leptosol (tanah tipis)",
    "Luvisol": "Luvisol (tanah liat)",
    "Nitisol": "Nitisol (tanah subur)",
    "Phaeozem": "Phaeozem",
    "Planosol": "Planosol",
    "Plinthosol": "Plinthosol",
    "Podzol": "Podzol (tanah pasir)",
    "Regosol": "Regosol (tanah longgar)",
    "Solonchak": "Solonchak (tanah asin)",
    "Solonetz": "Solonetz (tanah soda)",
    "Umbrisol": "Umbrisol",
    "Vertisol": "Vertisol (tanah lempung)",
}


async def get_soil_type(lat: float, lon: float, timeout: float = 8.0) -> dict | None:
    params = {"lat": lat, "lon": lon}

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(ISRIC_SOILGRIDS_URL, params=params)
            resp.raise_for_status()
            data = resp.json()

            wrb = data.get("wrb_class_name")
            if not wrb or wrb == "No information":
                logger.info("No soil data at (%.4f, %.4f)", lat, lon)
                return None

            risk_score = WRB_RISK_MAP.get(wrb, 0.5)
            name_id = WRB_NAME_MAP.get(wrb, wrb)

            result = {
                "code": wrb,
                "name": name_id,
                "risk_score": risk_score,
            }

            logger.info(
                "Soil at (%.4f, %.4f): %s (risk=%.2f)",
                lat, lon, wrb, risk_score,
            )
            return result

    except Exception as e:
        logger.warning("Soil fetch failed for (%.4f, %.4f): %s", lat, lon, e)
        return None
