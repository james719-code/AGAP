import L from 'leaflet';

const CACHE_NAME = 'agap-gis-tile-cache-v1';

/**
 * Custom Leaflet TileLayer that caches tile images in CacheStorage/IndexedDB for offline availability.
 */
export class OfflineTileLayer extends L.TileLayer {
  createTile(coords: L.Coords, done: L.DoneCallback): HTMLImageElement {
    const tile = document.createElement('img');
    const url = this.getTileUrl(coords);

    tile.alt = '';
    tile.setAttribute('role', 'presentation');

    // Attempt to fetch tile from CacheStorage first
    this.fetchTileWithCache(url)
      .then((blobUrl) => {
        tile.src = blobUrl;
        done(undefined, tile);
      })
      .catch(() => {
        // Fallback directly to URL if cache lookup fails
        tile.src = url;
        done(undefined, tile);
      });

    return tile;
  }

  private async fetchTileWithCache(url: string): Promise<string> {
    if (!('caches' in window)) {
      return url;
    }

    try {
      const cache = await caches.open(CACHE_NAME);
      const cachedResponse = await cache.match(url);

      if (cachedResponse) {
        const blob = await cachedResponse.blob();
        return URL.createObjectURL(blob);
      }

      // Fetch from network and store in CacheStorage
      const response = await fetch(url, { mode: 'cors' });
      if (response.ok) {
        cache.put(url, response.clone()).catch(() => {
          // Ignore cache write error in private modes
        });
        const blob = await response.blob();
        return URL.createObjectURL(blob);
      }
    } catch {
      // Offline fallback: ignore network error if un-cached
    }

    return url;
  }
}

/**
 * Pre-caches tiles for Legazpi City bounding box (zoom level 12 to 15)
 */
export async function preCacheLegazpiTiles(progressCallback?: (loaded: number, total: number) => void): Promise<number> {
  if (!('caches' in window)) return 0;

  const cache = await caches.open(CACHE_NAME);
  // Key bounding coordinates around Legazpi City & Mt. Mayon slopes
  const minLat = 13.10;
  const maxLat = 13.18;
  const minLon = 123.70;
  const maxLon = 123.78;
  const zoom = 14;

  const latToTileY = (lat: number, z: number) =>
    Math.floor(((1 - Math.log(Math.tan((lat * Math.PI) / 180) + 1 / Math.cos((lat * Math.PI) / 180)) / Math.PI) / 2) * Math.pow(2, z));

  const lonToTileX = (lon: number, z: number) =>
    Math.floor(((lon + 180) / 360) * Math.pow(2, z));

  const minX = lonToTileX(minLon, zoom);
  const maxX = lonToTileX(maxLon, zoom);
  const minY = latToTileY(maxLat, zoom);
  const maxY = latToTileY(minLat, zoom);

  const urls: string[] = [];
  const subdomains = ['a', 'b', 'c'];

  for (let x = minX; x <= maxX; x++) {
    for (let y = minY; y <= maxY; y++) {
      const s = subdomains[(x + y) % subdomains.length];
      urls.push(`https://${s}.tile.openstreetmap.org/${zoom}/${x}/${y}.png`);
    }
  }

  let count = 0;
  for (const url of urls) {
    try {
      const match = await cache.match(url);
      if (!match) {
        const res = await fetch(url, { mode: 'cors' });
        if (res.ok) await cache.put(url, res);
      }
      count++;
      if (progressCallback) progressCallback(count, urls.length);
    } catch {
      // ignore individual tile errors
    }
  }

  return count;
}
