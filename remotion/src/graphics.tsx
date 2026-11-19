import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';

const iconPaths = {
  signal: 'M3 9a15 15 0 0 1 6-3m4.5-.4A15 15 0 0 1 21 9M6 12a9 9 0 0 1 3-1.6m5.5-.1A9 9 0 0 1 18 12m-9 3a4.3 4.3 0 0 1 6 0m-3 4h.01M3 3l18 18',
  phone: 'M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2m3 16h2m-3-13h4',
  bluetooth: 'm6 6 12 12-6 4V2l6 4L6 18',
  cross: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z',
  pin: 'M19 10c0 5-7 11-7 11s-7-6-7-11a7 7 0 0 1 14 0zM12 12a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z',
  person: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0zm-11 14v-2a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v2',
  check: 'm5 12 4 4L19 6',
  arrow: 'M3 12h17m-6-6 6 6-6 6',
  wifi: 'M3 9a15 15 0 0 1 18 0M6.5 12.5a9 9 0 0 1 11 0M9.5 16a4 4 0 0 1 5 0M12 20h.01',
};

export function Icon({name}: {name: keyof typeof iconPaths}) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      <path d={iconPaths[name]} />
    </svg>
  );
}

const contourA = 'M-520 104 C-212 -104 77 -216 293 -126 C488 -45 420 93 635 152 C846 211 948 49 1133 80 C1390 123 1431 321 1553 400 C1640 455 1763 430 1980 323';
const contourB = 'M-200 1114 C31 1083 186 1053 345 1090 C502 1126 628 1160 771 1058 C884 977 950 921 1166 956 C1372 991 1442 888 1538 777 C1633 666 1807 725 2076 602';

export function Topography() {
  return (
    <div className="backdrop" aria-hidden="true">
      <svg viewBox="0 0 1920 1080" fill="none">
        {Array.from({length: 16}, (_, index) => (
          <g key={index} stroke="currentColor" strokeWidth="1.4" opacity={0.13 + index * 0.008}>
            <path d={contourA} transform={`translate(${index * 9}, ${index * 24 - 275})`} />
            <path d={contourB} transform={`translate(${index * 5}, ${index * 24 - 182})`} />
          </g>
        ))}
      </svg>
    </div>
  );
}

const nodes = [
  {at: 137, title: 'Resident SOS', icon: 'phone'},
  {at: 597, title: 'Nearby phone', icon: 'bluetooth'},
  {at: 1057, title: 'Nearby phone', icon: 'bluetooth'},
  {at: 1517, title: 'Response team', icon: 'cross'},
] as const;

export function Network({online}: {online: boolean}) {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const time = frame / fps;
  const routeTime = online ? time - 0.8 : time - 4.45;
  const travelTime = online ? 2.2 : 5.2;
  const progress = interpolate(routeTime, [0, travelTime], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  const start = nodes[0].at;
  const end = nodes[3].at;
  const x = start + (end - start) * progress;
  const accent = online ? '#eac18e' : '#65e4d1';

  return (
    <div className="network" style={{color: accent}}>
      <div className="lane-heading">
        <div className="lane-icon"><Icon name={online ? 'wifi' : 'bluetooth'} /></div>
        <div>{online ? 'Online via mobile data or Wi-Fi' : 'Offline via Bluetooth relay'}</div>
      </div>
      <svg className="network-wire" viewBox="0 0 1654 200" fill="none">
        <path d={`M${start} 104H${end}`} stroke="currentColor" strokeOpacity=".34" strokeWidth="3" strokeDasharray={online ? undefined : '8 12'} />
        <path d={`M${start} 104H${x}`} stroke="currentColor" strokeWidth="4" />
        <circle cx={x} cy="104" r="22" fill="currentColor" opacity=".15" />
        <circle cx={x} cy="104" r="10" fill="currentColor" />
      </svg>
      {nodes.map((node, index) => {
        if (online && (index === 1 || index === 2)) return null;
        const reached = x >= node.at;
        return (
          <div className={`network-node${reached ? ' reached' : ''}`} key={node.at} style={{left: node.at}}>
            <div className="node-icon"><Icon name={node.icon} /></div>
            <span>{node.title}</span>
          </div>
        );
      })}
    </div>
  );
}
