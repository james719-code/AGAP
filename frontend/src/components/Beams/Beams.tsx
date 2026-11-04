import { lazy, Suspense, useState } from 'react';
import { supports3DBeams } from './capabilities';

export interface BeamsProps {
  beamWidth?: number;
  beamHeight?: number;
  beamNumber?: number;
  lightColor?: string;
  speed?: number;
  noiseIntensity?: number;
  scale?: number;
  rotation?: number;
}

// Check device capabilities before downloading the WebGL renderer.
const BeamsScene = lazy(() => import('./BeamsScene'));

const CssBeamsFallback = () => (
  <div className="w-full h-full relative overflow-hidden bg-black flex items-center justify-center pointer-events-none select-none">
    <div className="absolute inset-0 opacity-40 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-blue-900/40 via-slate-950 to-black" />
    <div
      className="absolute -inset-full opacity-25 bg-gradient-to-r from-blue-600/30 via-emerald-500/20 to-orange-500/30 blur-3xl animate-pulse"
      style={{ animationDuration: '8s' }}
    />
    <div className="absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-black via-black/80 to-transparent" />
  </div>
);

export default function Beams(props: BeamsProps) {
  const [render3D] = useState(supports3DBeams);
  if (!render3D) return <CssBeamsFallback />;
  return (
    <Suspense fallback={<CssBeamsFallback />}>
      <BeamsScene {...props} />
    </Suspense>
  );
}
