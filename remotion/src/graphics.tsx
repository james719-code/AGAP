import type {CSSProperties} from 'react';

export const iconPaths = {
  signal: 'M3 9a15 15 0 0 1 6-3m4.5-.4A15 15 0 0 1 21 9M6 12a9 9 0 0 1 3-1.6m5.5-.1A9 9 0 0 1 18 12m-9 3a4.3 4.3 0 0 1 6 0m-3 4h.01M3 3l18 18',
  phone: 'M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2m3 16h2m-3-13h4',
  bluetooth: 'm6 6 12 12-6 4V2l6 4L6 18',
  cross: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z',
  pin: 'M19 10c0 5-7 11-7 11s-7-6-7-11a7 7 0 0 1 14 0zM12 12a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z',
  check: 'm5 12 4 4L19 6',
  arrow: 'M3 12h17m-6-6 6 6-6 6',
  wifi: 'M3 9a15 15 0 0 1 18 0M6.5 12.5a9 9 0 0 1 11 0M9.5 16a4 4 0 0 1 5 0M12 20h.01',
  radio: 'M12 12h.01M8.2 8.2a5.4 5.4 0 0 0 0 7.6m7.6-7.6a5.4 5.4 0 0 1 0 7.6M5 5a10 10 0 0 0 0 14M19 5a10 10 0 0 1 0 14',
  monitor: 'M3 4h18v12H3zM12 16v4m-4 0h8',
  cloud: 'M6 18a4 4 0 0 1-.6-8A6.5 6.5 0 0 1 18 9a4.6 4.6 0 0 1 0 9H6',
  fire: 'M12 2c2 5 6 5 6 11a6 6 0 1 1-12 0c0-3 2-5 3-6 0 4 3 3 3-5z',
  car: 'M3 15V9l2-4h14l2 4v6M3 13h18M5 15v3m14-3v3M6 10h2m8 0h2',
  shield: 'M12 2 3 6v6c0 5 4 8 9 10 5-2 9-5 9-10V6l-9-4z',
  alert: 'M12 3 2 21h20L12 3zm0 7v5m0 3h.01',
  bolt: 'm13 2-9 12h7l-1 8 10-12h-7l1-8',
} as const;
export type IconName = keyof typeof iconPaths;

export function Icon({name, style}: {name: IconName; style?: CSSProperties}) {
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" style={style}><path d={iconPaths[name]}/></svg>;
}

export type Point = [number, number];
const lerp = (a:number,b:number,t:number)=>a+(b-a)*t;

/** Animated SVG path and actual moving data packet; all coordinates are composition pixels. */
export function RouteArc({from, to, lift=100, progress, color='#F2A540', width=8, dashed=false}: {
  from:Point;to:Point;lift?:number;progress:number;color?:string;width?:number;dashed?:boolean;
}) {
  const p=Math.max(0,Math.min(1,progress));
  const control:Point=[(from[0]+to[0])/2,(from[1]+to[1])/2-lift];
  const at=(t:number):Point=>[lerp(lerp(from[0],control[0],t),lerp(control[0],to[0],t),t),lerp(lerp(from[1],control[1],t),lerp(control[1],to[1],t),t)];
  const point=at(p), tail=at(Math.max(0,p-.045));
  const d=`M${from.join(' ')} Q${control.join(' ')} ${to.join(' ')}`;
  return <svg className="route-svg" viewBox="0 0 1920 1080" fill="none">
    <path d={d} stroke={color} strokeWidth="3" opacity=".30" strokeDasharray={dashed?'11 16':undefined}/>
    {p>0 && <path d={d} pathLength="1" stroke={color} strokeWidth={width} strokeDasharray={`${p} 1`} strokeLinecap="round"/>}
    {p>0 && p<1 && <>
      <circle cx={tail[0]} cy={tail[1]} r="8" fill={color} opacity=".6"/>
      <circle cx={point[0]} cy={point[1]} r="32" fill={color} opacity=".20"/>
      <circle cx={point[0]} cy={point[1]} r="14" fill={color} stroke="#fff" strokeWidth="3"/>
    </>}
  </svg>;
}
