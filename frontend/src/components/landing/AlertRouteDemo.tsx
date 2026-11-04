import { useId, useState } from 'react';
import { Radio, ShieldCheck, SignalHigh, Smartphone } from 'lucide-react';
import camsurMap from '@/assets/maps/camsur.svg';
import './alert-route.css';

const paths = {
  relay: {
    label: 'Bluetooth relay',
    route: 'M113 98 C163 98 168 182 226 182 S304 139 354 139 S444 96 491 96',
    description: 'Nearby AGAP phones pass the alert to a connected device.',
  },
  direct: {
    label: 'Mobile data',
    route: 'M113 98 C235 39 368 45 491 96',
    description: 'With mobile data, AGAP sends the alert directly to dispatch.',
  },
};

const nodes = [
  { x: 113, y: 98, label: 'Your phone', icon: Smartphone },
  { x: 226, y: 182, label: 'Nearby phone', icon: Radio, relay: true },
  { x: 354, y: 139, label: 'Connected phone', icon: Radio, relay: true },
  { x: 491, y: 96, label: 'Dispatch', icon: ShieldCheck },
];

export function AlertRouteDemo({ animate }: { animate: boolean }) {
  const [mode, setMode] = useState<keyof typeof paths>('relay');
  const waterPatternId = useId();
  const route = paths[mode];
  const alternate = mode === 'relay' ? paths.direct.route : paths.relay.route;

  return (
    <figure className="alert-route" data-mode={mode} aria-label="Illustrative alert delivery routes on a map of Camarines Sur">
      <div className="alert-route-header">
        <div className="flex items-center gap-2 text-sm font-bold">
          <span className="alert-route-dot" aria-hidden="true" />
          Follow an alert
        </div>
        <span className="alert-route-label">Delivery demo</span>
      </div>

      <svg className="alert-route-map" viewBox="0 0 640 318" aria-hidden="true">
        <defs>
          <pattern id={waterPatternId} width="14" height="14" patternUnits="userSpaceOnUse">
            <circle cx="7" cy="7" r="1" fill="#8dbcb2" opacity=".22" />
          </pattern>
        </defs>
        <rect width="640" height="318" fill={`url(#${waterPatternId})`} />
        <image href={camsurMap} width="640" height="318" />
        <g className="alert-route-water-labels" textAnchor="middle">
          <text x="243" y="102">San Miguel</text>
          <text x="243" y="122">Bay</text>
          <text x="109" y="247">Ragay Gulf</text>
          <text x="493" y="201">Lagonoy Gulf</text>
        </g>
        <g className="alert-route-map-key">
          <text className="alert-route-map-title" x="614" y="26" textAnchor="end">Camarines Sur</text>
          <text x="614" y="46" textAnchor="end">Illustrative routes</text>
          <text x="603" y="247" textAnchor="middle">N</text>
          <path d="M603 254l-7 19 7-4 7 4Z" />
        </g>
        <path className="alert-route-alternate" d={alternate} />
        <path className="alert-route-underlay" d={route.route} />
        <path className="alert-route-active" d={route.route} />

        {nodes.map(({ x, y, label, icon: Icon, relay }) => (
          <g key={label} className={relay && mode === 'direct' ? 'alert-route-node is-inactive' : 'alert-route-node'}>
            <circle className="alert-route-node-ring" cx={x} cy={y} r="25" />
            <circle className="alert-route-node-core" cx={x} cy={y} r="19" />
            <Icon x={x - 11} y={y - 11} width="22" height="22" strokeWidth={1.7} />
            <text x={x} y={y + 43} textAnchor="middle">{label}</text>
          </g>
        ))}

        {animate && (
          <circle key={mode} r="4.5" className="alert-route-packet">
            <animateMotion path={route.route} dur="4s" repeatCount="indefinite" />
          </circle>
        )}
      </svg>

      <div className="alert-route-controls" role="group" aria-label="Delivery method">
        {(Object.keys(paths) as (keyof typeof paths)[]).map((key) => {
          const Icon = key === 'relay' ? Radio : SignalHigh;
          return (
            <button key={key} type="button" aria-pressed={mode === key} onClick={() => setMode(key)}>
              <Icon aria-hidden="true" className="size-4 shrink-0" />
              {paths[key].label}
            </button>
          );
        })}
      </div>
      <figcaption className="alert-route-caption">
        <p aria-live="polite">{route.description}</p>
        <p className="alert-route-credit">
          Boundaries adapted from <a href="https://www.geoboundaries.org/api/current/gbOpen/PHL/ADM2/">geoBoundaries</a>
          {' (NAMRIA, PSA, OCHA Philippines) · '}
          <a href="https://creativecommons.org/licenses/by/3.0/igo/">CC BY 3.0 IGO</a>
        </p>
      </figcaption>
    </figure>
  );
}
