from dataclasses import dataclass, field

RISK_LABELS = ["RENDAH", "SEDANG", "TINGGI", "SANGAT_TINGGI"]


@dataclass
class FactorData:
    name: str
    raw_value: str
    score: float
    weight: float
    weighted_score: float
    label: str


@dataclass
class RiskReport:
    ml_label: str
    ml_confidence: float
    final_score: float
    final_label: str
    factors: list[FactorData] = field(default_factory=list)
    is_upgraded: bool = False
    is_downgraded: bool = False


def ml_score(label: str, confidence: float) -> float:
    c = confidence
    if label == "AMAN":
        if c >= 0.70:
            return 0.1
        if c >= 0.50:
            return 0.2
        return 0.3
    if label == "WASPADA":
        if c >= 0.70:
            return 0.5
        if c >= 0.50:
            return 0.6
        return 0.7
    if label == "BAHAYA":
        if c >= 0.70:
            return 0.8
        if c >= 0.50:
            return 0.9
        return 1.0
    return 0.5


def slope_score(degrees: float) -> float:
    if degrees < 8:
        return 0.1
    if degrees < 15:
        return 0.4
    if degrees < 25:
        return 0.7
    return 1.0


def rain_score(mm: float) -> float:
    if mm <= 0:
        return 0.0
    if mm < 5:
        return 0.2
    if mm < 15:
        return 0.5
    if mm < 30:
        return 0.8
    return 1.0


def elevation_score(meters: float) -> float:
    if meters < 200:
        return 0.1
    if meters < 500:
        return 0.4
    if meters < 1000:
        return 0.7
    return 1.0


def label_from_score(score: float) -> str:
    if score <= 0.2:
        return "RENDAH"
    if score <= 0.5:
        return "SEDANG"
    if score <= 0.8:
        return "TINGGI"
    return "SANGAT_TINGGI"


def result_from_score(score: float) -> str:
    if score <= 0.33:
        return "AMAN"
    if score <= 0.66:
        return "WASPADA"
    return "BAHAYA"


def analyze(
    ml_label: str,
    ml_confidence: float,
    slope_deg: float | None = None,
    rain_mm: float | None = None,
    elevation_m: float | None = None,
    soil_data: dict | None = None,
) -> RiskReport:
    factors: list[FactorData] = []
    total_weight = 0.0
    weighted_sum = 0.0
    missing_any = False

    def add_factor(name: str, raw: str, score: float, weight: float) -> None:
        nonlocal total_weight, weighted_sum
        total_weight += weight
        weighted_sum += score * weight
        factors.append(FactorData(
            name=name,
            raw_value=raw,
            score=score,
            weight=weight,
            weighted_score=round(score * weight, 3),
            label=label_from_score(score),
        ))

    ml_score_val = ml_score(ml_label, ml_confidence)
    ml_raw = f"{ml_label} {ml_confidence:.0%}"
    add_factor("Analisis Visual", ml_raw, ml_score_val, 0.50)

    if slope_deg is not None:
        add_factor(
            "Kemiringan Lereng",
            f"{slope_deg:.0f}°",
            slope_score(slope_deg),
            0.20,
        )
    else:
        missing_any = True

    if rain_mm is not None:
        add_factor(
            "Curah Hujan",
            f"{rain_mm:.0f} mm",
            rain_score(rain_mm),
            0.15,
        )
    else:
        missing_any = True

    if elevation_m is not None:
        add_factor(
            "Ketinggian",
            f"{elevation_m:.0f} m",
            elevation_score(elevation_m),
            0.10,
        )
    else:
        missing_any = True

    if soil_data is not None:
        add_factor(
            "Jenis Tanah",
            soil_data.get("name", soil_data.get("code", "?")),
            soil_data.get("risk_score", 0.5),
            0.05,
        )
    else:
        missing_any = True

    final_score = (
        weighted_sum / total_weight if missing_any and total_weight > 0 else weighted_sum
    )
    final_score = max(0.0, min(1.0, final_score))
    final_label = result_from_score(final_score)

    is_upgraded = final_score > ml_score_val + 0.05
    is_downgraded = final_score < ml_score_val - 0.05

    factors.sort(key=lambda f: f.weighted_score, reverse=True)

    return RiskReport(
        ml_label=ml_label,
        ml_confidence=ml_confidence,
        final_score=final_score,
        final_label=final_label,
        factors=factors,
        is_upgraded=is_upgraded,
        is_downgraded=is_downgraded,
    )
