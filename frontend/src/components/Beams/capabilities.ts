export function supports3DBeams(): boolean {
  try {
    const nav = navigator as Navigator & { deviceMemory?: number };
    if (
      window.innerWidth < 768
      || (typeof nav.deviceMemory === 'number' && nav.deviceMemory <= 4)
      || (typeof nav.hardwareConcurrency === 'number' && nav.hardwareConcurrency <= 4)
      || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    ) {
      return false;
    }

    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
    if (!gl) return false;
    // The renderer creates its own context; release this temporary probe.
    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return true;
  } catch {
    return false;
  }
}
