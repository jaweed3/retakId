import os
import shutil
import logging
from pathlib import Path

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

RAW_DIR = "backend/data/raw"
PROCESSED_DIR = "backend/data/processed"

MAPPING = {
    # AMAN
    "agricultural_land_soil_cracks": "AMAN",
    "clean_mountain_path": "AMAN",
    "clear_sky_mountain_ridge": "AMAN",
    "farm_land_healthy_crops": "AMAN",
    "forest_park_landscape": "AMAN",
    "grassy_field": "AMAN",
    "green_tea_plantation_hills": "AMAN",
    "healthy_mountain_forest": "AMAN",
    "healthy_pine_forest": "AMAN",
    "healthy_tropical_forest_floor": "AMAN",
    "lush_green_hill_slope": "AMAN",
    "mountain_valley_floor": "AMAN",
    "mountain_village_landscape": "AMAN",
    "normal_clay_ground": "AMAN",
    "normal_garden_soil": "AMAN",
    "park_slope_stability": "AMAN",
    "paved_road_no_cracks": "AMAN",
    "stable_limestone_cliff": "AMAN",
    "stable_river_bank": "AMAN",
    "stable_rock_formation": "AMAN",
    "standard_grass_lawn": "AMAN",
    "vineyard_slope_stability": "AMAN",

    # WASPADA
    "alluvial_soil_cracks": "WASPADA",
    "arctic_permafrost_cracks": "WASPADA",
    "bog_soil_cracks": "WASPADA",
    "bridge_foundation_soil_cracks": "WASPADA",
    "camping_ground_soil_cracks": "WASPADA",
    "cemetery_soil_subsidence": "WASPADA",
    "clay_soil_fissures": "WASPADA",
    "cliff_edge_cracks": "WASPADA",
    "coastal_cliff_erosion_cracks": "WASPADA",
    "construction_site_soil_cracks": "WASPADA",
    "cracked_concrete_slope": "WASPADA",
    "creep_soil_cracks": "WASPADA",
    "desiccation_cracks_soil": "WASPADA",
    "drainage_failure_cracks": "WASPADA",
    "drought_soil_cracking_deep": "WASPADA",
    "dry_soil_cracks_deep": "WASPADA",
    "earth_fissure_hazard": "WASPADA",
    "expansive_soil_cracks": "WASPADA",
    "fissure_soil_erosion_landslide": "WASPADA",
    "frozen_ground_fissures": "WASPADA",
    "gabion_wall_failure_signs": "WASPADA",
    "geohazard_soil_cracks": "WASPADA",
    "geological_soil_fissure": "WASPADA",
    "geotechnical_failure_cracks": "WASPADA",
    "ground_crack_disaster_signs": "WASPADA",
    "ground_subsidence_fissures": "WASPADA",
    "historic_site_ground_cracks": "WASPADA",
    "karst_topography_fissures": "WASPADA",
    "landslide_fissure_ground": "WASPADA",
    "landslide_mitigation_failure": "WASPADA",
    "limestone_cave_soil_cracks": "WASPADA",
    "loess_soil_vertical_cracks": "WASPADA",
    "mass_wasting_cracks": "WASPADA",
    "monsoon_rain_soil_fissure": "WASPADA",
    "mountain_pass_soil_fissures": "WASPADA",
    "mountain_soil_fissure": "WASPADA",
    "pipe_burst_soil_erosion": "WASPADA",
    "quarry_ground_fissures": "WASPADA",
    "railway_embankment_cracks": "WASPADA",
    "rekahan_tanah_bukit": "WASPADA",
    "residential_area_ground_cracks": "WASPADA",
    "retaining_wall_cracks_soil": "WASPADA",
    "retaining_wall_leaning_soil": "WASPADA",
    "rice_terrace_landslide_signs": "WASPADA",
    "saturated_soil_fissures": "WASPADA",
    "scenic_lookout_ground_cracks": "WASPADA",
    "septic_tank_soil_subsidence": "WASPADA",
    "shale_slope_failure": "WASPADA",
    "silt_soil_erosion": "WASPADA",
    "sinkhole_formation_signs": "WASPADA",
    "snowmelt_soil_erosion": "WASPADA",
    "soil_cracks_landslide": "WASPADA",
    "soil_movement_cracks": "WASPADA",
    "subsidence_cracks": "WASPADA",
    "surface_cracks_slope_stability": "WASPADA",
    "tea_plantation_soil_cracks": "WASPADA",
    "thrawing_soil_landslide": "WASPADA",
    "torrential_rain_soil_cracks": "WASPADA",
    "typhoon_landslide_signs": "WASPADA",
    "urban_sinkhole_cracks": "WASPADA",
    "volcanic_soil_fissures": "WASPADA",
    "water_main_leak_ground_cracks": "WASPADA",
    "wet_soil_cracks": "WASPADA",

    # BAHAYA
    "bencana_pergerakan_tanah": "BAHAYA",
    "burnt_forest_landslide_cracks": "BAHAYA",
    "debris_flow_cracks": "BAHAYA",
    "deforestation_landslide_signs": "BAHAYA",
    "desert_soil_fissures": "WASPADA", # Added desert soil fissure to WASPADA
    "earthquake_ground_cracks": "BAHAYA",
    "erosion_gullies_landslide": "BAHAYA",
    "flash_flood_debris_cracks": "BAHAYA",
    "forest_slope_failure": "BAHAYA",
    "granite_rockfall_soil": "BAHAYA",
    "hurricane_soil_damage": "BAHAYA",
    "indonesia_landslide_cracks": "BAHAYA",
    "jungle_landslide_debris": "BAHAYA",
    "logging_area_soil_damage": "BAHAYA",
    "longsor_ponorogo_retakan": "BAHAYA",
    "mining_pit_wall_cracks": "BAHAYA",
    "mudslide_warning_signs": "BAHAYA",
    "open_pit_mine_slope_cracks": "BAHAYA",
    "pavement_cracks_landslide": "BAHAYA",
    "peat_soil_subsidence": "BAHAYA",
    "retakan_tanah_longsor": "BAHAYA",
    "road_collapse_landslide": "BAHAYA",
    "rockfall_soil_cracks": "BAHAYA",
    "rural_road_landslide_cracks": "BAHAYA",
    "sandstone_erosion_fissures": "BAHAYA",
    "sandy_soil_slope_failure": "BAHAYA",
    "slope_failure_cracks": "BAHAYA",
    "tailings_dam_failure_cracks": "BAHAYA",
    "tanah_belah_longsor": "BAHAYA",
    "tanah_retak_rawan_longsor": "BAHAYA",
    "tropical_soil_landslide_cracks": "BAHAYA",
    "wildfire_soil_erosion_signs": "BAHAYA",

    # NEW MAPPINGS
    "stable_embankment": "AMAN",
    "widening_earth_cracks": "WASPADA",
    "transverse_pavement_cracks": "WASPADA",
    "wet_soil_tension_cracks": "WASPADA",
    "hillside_cracks": "WASPADA",
    "polygonal_soil_cracking": "WASPADA",
    "leaning_telephone_poles_landslide": "WASPADA",
    "blocky_soil_fragments": "WASPADA",
    "dry_soil_deep_fissures": "WASPADA",
    "hiking_trail_slope_failure": "WASPADA",
    "foundation_settlement_cracks": "WASPADA",
    "longitudinal_ground_cracks": "WASPADA",
    "deep_soil_fissure_hazard": "WASPADA",
    "cracked_retaining_wall_close_up": "WASPADA",
    "erosion_rill_network": "WASPADA",
    "pavement_edge_cracking": "WASPADA",
    "soil_creep_signs": "WASPADA",
    "underground_cavity_sinkhole": "WASPADA",
    "settlement_cracks_soil": "WASPADA",
    "geological_fissure_surface": "WASPADA",
    "diagonal_slope_cracks": "WASPADA",
    "tensile_soil_cracks": "WASPADA",
    "hairline_soil_fissures": "WASPADA",
    "staircase_ground_cracking": "WASPADA",
    "collapsed_mountain_road": "BAHAYA",
    "landslide_warning_sign_red": "BAHAYA",
    "landslide_burial_site": "BAHAYA",
    "massive_ground_displacement": "BAHAYA",
    "massive_mudslide_damage": "BAHAYA",
    "landslide_victims_area": "BAHAYA",
    "active_soil_movement": "BAHAYA",
    "mudflow_path": "BAHAYA",
    "rapid_soil_erosion_damage": "BAHAYA",
    "fresh_landslide_scar": "BAHAYA",
    "landslide_mud_sea": "BAHAYA",
    "rockfall_destruction": "BAHAYA",
    "tilted_trees_landslide": "BAHAYA",
    "landslide_destroyed_house": "BAHAYA",
    "dangerous_cliff_collapse": "BAHAYA",
    "landslip_mountain_side": "BAHAYA",
    "broken_bridge_landslide": "BAHAYA",
    "landslide_debris_pile": "BAHAYA",
    "landslide_rescue_operation": "BAHAYA",
    "earthflow_damage": "BAHAYA",
}

def organize_images():
    # Create target directories
    for label in ["AMAN", "WASPADA", "BAHAYA"]:
        os.makedirs(os.path.join(PROCESSED_DIR, label), exist_ok=True)

    raw_path = Path(RAW_DIR)
    
    # Track missing mappings
    missing = []
    
    for sub_dir in raw_path.iterdir():
        if not sub_dir.is_dir():
            continue
            
        label = MAPPING.get(sub_dir.name)
        
        if not label:
            missing.append(sub_dir.name)
            continue
            
        logger.info(f"Processing {sub_dir.name} -> {label}")
        
        # Move all images
        count = 0
        for img_file in sub_dir.glob("*"):
            if img_file.suffix.lower() in [".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"]:
                dest = os.path.join(PROCESSED_DIR, label, f"{sub_dir.name}_{img_file.name}")
                shutil.copy2(img_file, dest) # Use copy2 to keep metadata, safer than move for now
                count += 1
        
        logger.info(f"  Copied {count} images")

    if missing:
        logger.warning(f"Missing mappings for: {missing}")

if __name__ == "__main__":
    organize_images()
    logger.info("Organization complete.")
