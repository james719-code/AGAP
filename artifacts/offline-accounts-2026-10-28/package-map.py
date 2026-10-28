"""Package existing Mapnik cache bytes as a test-only MBTiles archive; no network."""
import math
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = sqlite3.connect(Path(__file__).with_name("map-cache.db"))
destination = root / "android/app/src/offlineDebug/assets/agap/deployment.mbtiles"
destination.parent.mkdir(parents=True, exist_ok=True)
db = sqlite3.connect(destination)
db.executescript("CREATE TABLE IF NOT EXISTS metadata(name TEXT PRIMARY KEY,value TEXT); CREATE TABLE IF NOT EXISTS tiles(zoom_level INTEGER,tile_column INTEGER,tile_row INTEGER,tile_data BLOB,PRIMARY KEY(zoom_level,tile_column,tile_row)); DELETE FROM tiles; DELETE FROM metadata;")
coordinates = []
for key, data in source.execute("SELECT key,tile FROM tiles WHERE provider='Mapnik'"):
    zoom = next(z for z in range(1, 25) if z * 4**z <= key < (z + 1) * 4**z)
    size = 2**zoom
    x, y = divmod(key - zoom * size * size, size)
    db.execute("INSERT INTO tiles VALUES(?,?,?,?)", (zoom, x, size - 1 - y, data))
    coordinates.append((zoom, x, y))
assert coordinates and {z for z, _, _ in coordinates} == {15}
zoom = 15
size = 2**zoom
west = min(x for _, x, _ in coordinates) / size * 360 - 180
east = (max(x for _, x, _ in coordinates) + 1) / size * 360 - 180
lat = lambda y: math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / size))))
north = lat(min(y for _, _, y in coordinates))
south = lat(max(y for _, _, y in coordinates) + 1)
metadata = {"name": "AGAP Offline Test Area", "type": "baselayer", "version": "1.0", "format": "png", "minzoom": "15", "maxzoom": "15", "bounds": f"{west},{south},{east},{north}", "center": "123.7438,13.1391,15", "attribution": "© OpenStreetMap contributors", "description": "Existing test-session cache only; limited sample area, not a full deployment map."}
db.executemany("INSERT INTO metadata VALUES(?,?)", metadata.items())
db.commit()
print(f"Packaged {len(coordinates)} existing tiles at zoom 15; bounds {metadata['bounds']}")
db.close()
source.close()
