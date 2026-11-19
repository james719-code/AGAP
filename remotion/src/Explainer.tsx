import {Audio} from '@remotion/media';
import type {CSSProperties, ReactNode} from 'react';
import {
  AbsoluteFill, Easing, Img, Sequence, interpolate, spring, staticFile, useCurrentFrame,
} from 'remotion';
import {Icon, RouteArc, type IconName, type Point} from './graphics';

export const fps = 30;
export const durationInFrames = 48 * fps;
// Every cut lands on a beat of the new 120 BPM instrumental.
const boundaries = [0, 6, 14, 19, 29, 37, 43, 48];
const ease = {extrapolateLeft:'clamp',extrapolateRight:'clamp',easing:Easing.bezier(.16,1,.3,1)} as const;
const linear = {extrapolateLeft:'clamp',extrapolateRight:'clamp'} as const;
const art = (path:string)=>staticFile(path);
const p=(f:number,start:number,len=25)=>interpolate(f,[start,start+len],[0,1],ease);
const ramp=(f:number,start:number,end:number)=>interpolate(f,[start,end],[0,1],linear);
const pop=(f:number,start:number)=>spring({frame:Math.max(0,f-start),fps,config:{mass:.75,damping:16,stiffness:185}});
const atlas='illustrations/modern/agap-character-atlas.png';
const actionAtlas='illustrations/modern/agap-action-atlas.png';
const storm='illustrations/modern/storm-city.png';

function Enter({children,delay=0,x=0,y=32,className='',style}:{
  children:ReactNode;delay?:number;x?:number;y?:number;className?:string;style?:CSSProperties;
}) {
  const f=useCurrentFrame(), v=p(f,delay,26);
  return <div className={className} style={{...style,opacity:v,transform:`translate3d(${x*(1-v)}px,${y*(1-v)}px,0)`}}>{children}</div>;
}

function Wordmark({className=''}:{className?:string}) {
  return <Img className={`wordmark ${className}`} src={art('logos/agap-wordmark.png')} alt="AGAP PH"/>;
}
function BrandBug({dark=false}:{dark?:boolean}) {
  return <div className={`brand-bug ${dark?'on-dark':''}`}><Wordmark/></div>;
}
function Stage({children,tone=''}:{children:ReactNode;tone?:string}) {
  return <AbsoluteFill className={`stage ${tone}`}>{children}</AbsoluteFill>;
}
function TypeLine({text,delay=0,className='',style}:{
  text:string;delay?:number;className?:string;style?:CSSProperties;
}) {
  const f=useCurrentFrame();
  return <div className={`type-line ${className}`} style={style}>
    {text.split(' ').map((word,i)=>{
      const v=pop(f,delay+i*4);
      return <span style={{opacity:p(f,delay+i*4,9),transform:`translateY(${(1-v)*110}px) skewY(${(1-v)*5}deg)`}} key={i}>{word}{i<text.split(' ').length-1?'\u00a0':''}</span>;
    })}
  </div>;
}

/** Each third of the generated alpha atlas is an independently animated sprite. */
function Character({index,x,y,height,delay=0,travel=0,lean=0,active=false,shadow=true,actionAt=Infinity,actionEnd=Infinity}:{
  index:0|1|2;x:number;y:number;height:number;delay?:number;travel?:number;lean?:number;active?:boolean;shadow?:boolean;actionAt?:number;actionEnd?:number;
}) {
  const f=useCurrentFrame(), v=p(f,delay,34), springIn=pop(f,delay);
  // The exact gutters of both atlases are preserved, including the responder's moving bag.
  const crop=[{start:0,width:512},{start:512,width:448},{start:960,width:576}][index];
  const rasterScale=height/1024, width=crop.width*rasterScale;
  const action=f>=actionAt&&f<actionEnd;
  const nudge=Number.isFinite(actionAt)?p(f,actionAt,15)*(1-(Number.isFinite(actionEnd)?p(f,actionEnd,15):0)):0;
  const bob=(active?Math.sin(f/8+index)*4:Math.sin(f/19+index)*2.5)-Math.sin(nudge*Math.PI)*12;
  const angle=(1-v)*lean+(active?Math.sin(f/13+index)*.8:0);
  return <div className="character" style={{left:x+(height/2-width)/2,top:y,width,height,opacity:p(f,delay,14),transform:`translate3d(${travel*(1-v)+(index===2?25:7)*nudge}px,${(1-springIn)*30+bob}px,0) rotate(${angle}deg) scale(${.89+.11*springIn})`}}>
    {shadow && <div className="character-shadow"/>}
    <div className="sprite-crop">
      <Img src={art(atlas)} alt={['Resident with a smartphone','Community relay volunteer','Emergency responder'][index]} style={{width:1536*rasterScale,height,left:-crop.start*rasterScale,opacity:action?0:1}}/>
      <Img src={art(actionAtlas)} alt="" style={{width:1536*rasterScale,height,left:-crop.start*rasterScale,opacity:action?1:0}}/>
    </div>
  </div>;
}

function RingBurst({x,y,frame,delay=0,size=130,color='#F2A540',loop=false}:{
  x:number;y:number;frame:number;delay?:number;size?:number;color?:string;loop?:boolean;
}) {
  return <div className="ring-burst" style={{left:x,top:y,width:size,height:size,color}}>
    {[0,1,2].map(i=>{
      const age=frame-delay-i*9;
      const state=age<0?0:loop?(age%55)/55:Math.min(1,age/55);
      return <i key={i} style={{opacity:age<0?0:(1-state)*.75,transform:`scale(${.65+state*1.55})`}}/>;
    })}
  </div>;
}

function StormPlate({intensity=1}:{intensity?:number}) {
  const f=useCurrentFrame();
  return <>
    <Img className="storm-plate" src={art(storm)} alt="Modern city neighborhood during a rainstorm" style={{transform:`translateX(${-18-f*.14}px) scale(${1.065+f*.00025})`}}/>
    <div className="storm-shade" style={{opacity:.58*intensity}}/>
    <svg className="rain" viewBox="0 0 1920 1080" opacity={.56*intensity}>
      {Array.from({length:104},(_,i)=>{
        const x=((i*193+87-f*(3+i%5))%2000+2000)%2000-20;
        const y=(i*83+f*(12+i%8))%1140-50;
        const length=12+(i%5)*8;
        return <line key={i} x1={x} y1={y} x2={x-8-(i%4)*2} y2={y+length} stroke={i%5===0?'#e2f3ff':'#8abcc9'} strokeWidth={i%4===0?3:1.6} opacity={.26+(i%4)*.13}/>;
      })}
    </svg>
  </>;
}

function Hook() {
  const f=useCurrentFrame();
  const lost=ramp(f,36,58), second=p(f,92,20);
  const shake=f>=35 && f<55?Math.sin((f-35)*1.7)*(1-ramp(f,35,55))*7:0;
  return <Stage tone="cinematic">
    <StormPlate/>
    <div className="storm-flash" style={{opacity:Math.max(0,1-Math.abs(f-35)/4)*.16}}/>
    <BrandBug dark/>
    <div className="hook-heading" style={{transform:`translateX(${shake}px)`}}>
      <Enter className="eyebrow dark-eyebrow" delay={2}><i/> WHEN IT MATTERS MOST</Enter>
      <div className="hook-words">
        <div style={{position:'absolute',opacity:1-second,transform:`translateY(${-110*second}px)`}}>
          <TypeLine text="No" delay={11}/>
          <TypeLine text="signal?" delay={21} className="amber"/>
        </div>
        <div style={{position:'absolute',opacity:second,transform:`translateY(${110*(1-second)}px)`}}>
          <TypeLine text="Still" delay={90}/>
          <TypeLine text="a way." delay={101} className="amber"/>
        </div>
      </div>
      <Enter className="hook-sub" delay={111}>An emergency doesn't wait.</Enter>
    </div>
    <Character index={0} x={1252} y={314} height={678} delay={13} travel={200} lean={-7} active actionAt={54}/>
    <RingBurst x={1667} y={272} frame={f} size={104} delay={45} color="#F2A540" loop/>
    <div className="tower-off" style={{left:1667,top:272,opacity:lost,scale:.65+.35*pop(f,40)}}><Icon name="signal"/></div>
    <Enter className="signal-hud" delay={17} x={-70}>
      <div className="signal-hud-icon" style={{background:lost>.5?'var(--red)':'var(--teal)'}}>
        <Icon name={lost>.5?'signal':'wifi'}/>
      </div>
      <div><small>NETWORK STATUS</small><strong>{lost>.5?'Mobile data lost':'Connected'}</strong>
        <div className="signal-bars">{[0,1,2,3,4].map(i=><i key={i} style={{height:9+i*6,opacity:1-ramp(f,32+(4-i)*4,41+(4-i)*4)}}/>)}</div>
      </div>
    </Enter>
    <div className="hook-bottom"><i/> AGAP · emergency assistance</div>
  </Stage>;
}

const dialOptions: {label:string;icon:IconName;x:number;y:number;color:string}[] = [
  {label:'Medical',icon:'cross',x:0,y:-205,color:'#176e7e'},
  {label:'Fire',icon:'fire',x:205,y:-61,color:'#B93323'},
  {label:'Accident',icon:'car',x:137,y:170,color:'#b57525'},
  {label:'Disaster',icon:'alert',x:-137,y:170,color:'#176e7e'},
  {label:'Crime',icon:'shield',x:-205,y:-61,color:'#172c29'},
];

function SosDemo() {
  const f=useCurrentFrame(), drag=p(f,62,59), release=pop(f,137);
  const active=f>=62&&f<137, confirmed=p(f,137,18);
  const phoneIn=pop(f,2);
  const phoneY=64+(1-phoneIn)*250;
  const dialOut=p(f,177,26);
  return <Stage tone="light demo">
    <div className="demo-halo"/>
    <div className="demo-slab"/>
    <BrandBug/>
    <div className="phone-frame" style={{left:270+(1-phoneIn)*-280,top:147+(phoneY-64),transform:`rotate(${-8*(1-phoneIn)}deg) scale(${.86+.14*phoneIn})`,opacity:p(f,2,18)}}>
      <div className="phone-screen">
        <Img src={art('product/citizen-home.png')} alt="AGAP mobile application's real SOS screen"/>
        <div className="phone-drag-line" style={{height:`${11.65*drag}%`,opacity:active?1:0}}/>
        <div className="phone-pointer" style={{top:`${46.6-11.65*drag}%`,opacity:p(f,55,12)*(1-p(f,134,12)),scale:1+.1*Math.sin(f/7)}}/>
        <div className="phone-confirm" style={{opacity:confirmed,scale:.7+.3*release}}><Icon name="check"/></div>
      </div>
    </div>
    <div className="demo-copy">
      <Enter className="eyebrow" delay={7}><i/> THE AGAP APP</Enter>
      <TypeLine text="Send an SOS." delay={13}/>
      <div className="demo-caption" style={{opacity:p(f,23,14)*(1-dialOut)}}>{f<137?'Drag to the help you need.':'Release to send.'}</div>
    </div>
    <div className="dial-area" style={{opacity:p(f,26,18)*(1-dialOut),transform:`translateY(${(1-pop(f,25))*70}px) scale(${1-.2*dialOut})`}}>
      <div className="dial-base"/>
      <div className="dial-ring"/>
      <div className="dial-ring inner"/>
      {dialOptions.map((opt,i)=>{
        const v=pop(f,39+i*4), selected=i===0&&f>=100;
        return <div className={`dial-option ${selected?'selected':''}`} key={opt.label} style={{left:285+opt.x*v,top:285+opt.y*v,opacity:p(f,39+i*4,10),color:opt.color,scale:selected?1.12:1}}>
          <div><Icon name={opt.icon}/></div><span>{opt.label}</span>
        </div>;
      })}
      <svg className="dial-track" viewBox="0 0 570 570"><path d={`M285 285 V${285-205*drag}`} fill="none" stroke="#F2A540" strokeLinecap="round" strokeWidth="11" strokeDasharray="9 13" opacity={active?.85:0}/></svg>
      <div className="dial-puck" style={{left:285,top:285-205*drag,scale:1-.18*drag,opacity:1-confirmed}}><b>SOS</b><small>{active?'DRAG':'HOLD'}</small></div>
      {active && <div className="dial-touch" style={{left:285,top:285-205*drag,scale:1+.13*Math.sin(f/8)}}/>}
      <RingBurst x={285} y={80} frame={f} delay={136} color="#176e7e" size={83}/>
      <div className="dial-ok" style={{left:285,top:80,opacity:confirmed,scale:.64+.36*release}}><Icon name="check"/></div>
    </div>
    <div className="demo-send-card" style={{opacity:p(f,177,16),transform:`translateY(${(1-pop(f,177))*100}px) scale(${.84+.16*pop(f,177)})`}}>
      <div className="send-card-icon"><Icon name="cross"/></div>
      <div><span>SOS CREATED</span><strong>Medical</strong><small><Icon name="pin"/> Location when available</small></div>
      <div className="send-card-ready"><Icon name="check"/> Ready to route</div>
      <RingBurst x={69} y={68} frame={f} delay={180} color="#B93323" size={93}/>
    </div>
    <div className="demo-foot">Illustrated gesture · development screen</div>
  </Stage>;
}

function NetworkBackdrop({teal=false}:{teal?:boolean}) {
  const f=useCurrentFrame();
  return <>
    <div className={`network-backdrop ${teal?'network-teal':''}`}/>
    <svg className="network-grid" viewBox="0 0 1920 1080">
      <defs><pattern id="grid" width="64" height="64" patternUnits="userSpaceOnUse"><path d="M64 0H0V64" fill="none" stroke="#d0e5df" strokeWidth="1.3" opacity=".24"/></pattern></defs>
      <rect width="1920" height="1080" fill="url(#grid)" transform={`translate(${(f*.35)%64} ${(f*.15)%64})`}/>
      {[0,1,2,3,4].map(i=><circle key={i} cx="960" cy="620" r={250+i*155} fill="none" stroke="#a4d1c6" strokeWidth="1.1" opacity=".12"/>)}
    </svg>
  </>;
}
function NodeBadge({at,icon,color,frame,delay=0,active=false,size=118,pulseAt=delay}:{
  at:Point;icon:IconName;color:string;frame:number;delay?:number;active?:boolean;size?:number;pulseAt?:number;
}) {
  const v=pop(frame,delay);
  const pulse=Math.max(0,1-(frame-pulseAt)/22);
  return <>
    {active && <RingBurst x={at[0]} y={at[1]} frame={frame} delay={pulseAt} size={size} color={color} loop/>}
    <div className={`node-badge ${active?'active':''}`} style={{left:at[0],top:at[1],width:size,height:size,color:active?(color==='#F2A540'?'#172c29':'#fff'):color,background:active?color:'#203934',opacity:p(frame,delay,12),scale:(.65+.35*v)*(active&&frame>=pulseAt?1+.11*pulse:1),borderColor:color}}><Icon name={icon}/></div>
  </>;
}
function Online() {
  const f=useCurrentFrame(), src:Point=[410,505], cloud:Point=[960,400], end:Point=[1510,505];
  const path1=ramp(f,30,75),path2=ramp(f,75,116);
  return <Stage tone="dark">
    <NetworkBackdrop teal/>
    <BrandBug dark/>
    <div className="online-heading">
      <TypeLine text="Connected?" delay={3}/>
      <TypeLine text="Straight through." delay={10} className="amber"/>
    </div>
    <RouteArc from={src} to={cloud} lift={100} progress={path1}/>
    <RouteArc from={cloud} to={end} lift={100} progress={path2}/>
    <Character index={0} x={174} y={506} height={476} delay={4} travel={-150} lean={-5} active actionAt={18} actionEnd={117}/>
    <NodeBadge at={src} icon="phone" color="#B93323" frame={f} delay={16} active size={120}/>
    <NodeBadge at={cloud} icon="cloud" color="#F2A540" frame={f} delay={20} active={path1>=1} pulseAt={75} size={150}/>
    <NodeBadge at={end} icon="monitor" color="#176e7e" frame={f} delay={22} active={path2>=1} pulseAt={116} size={145}/>
    <Enter className="online-source node-caption" delay={20}><b>Resident SOS</b><span><i/> Mobile data / Wi-Fi</span></Enter>
    <Enter className="cloud-caption" delay={24}>Internet</Enter>
    <Enter className="online-destination" delay={22} x={90}>
      <div className="monitor-card">
        <div className="monitor-top"><i/><i/><i/><span>COMMAND CENTER</span></div>
        <div className="monitor-content">
          <div className="monitor-map"><svg viewBox="0 0 210 100"><path d="M0 80 75 50 122 75 210 15M0 20l95 40 55-60" fill="none" stroke="#b7cbc6" strokeWidth="6"/></svg><span style={{opacity:p(f,116,12),scale:pop(f,116)}}><Icon name="pin"/></span></div>
          <div className="monitor-bars"><i/><i/><i/><i/></div>
        </div>
      </div>
      <div className="delivery-status" style={{opacity:p(f,116,12),transform:`translateY(${(1-pop(f,116))*25}px)`}}><Icon name="check"/> SOS received</div>
    </Enter>
    <div className="online-foot">When a network connection is available.</div>
  </Stage>;
}

const meshNodes: Point[]=[[253,392],[725,392],[1198,392],[1668,392]];
const meshLabels=['Resident','Nearby phone','Reachable responder','Command Center'];
const meshColors=['#B93323','#176e7e','#176e7e','#F2A540'];
const meshIcons: IconName[]=['phone','bluetooth','radio','monitor'];

function Mesh() {
  const f=useCurrentFrame();
  const legs=[ramp(f,60,119),ramp(f,128,188),ramp(f,196,253)];
  const done=[true,legs[0]>=1,legs[1]>=1,legs[2]>=1];
  const zoom=1+.055*Math.sin(Math.PI*ramp(f,45,274));
  const cameraPan=36*(1-ramp(f,60,253))-18;
  return <Stage tone="dark mesh">
    <Img className="mesh-city" src={art(storm)} alt="" style={{transform:`scale(${1.1+f*.0003}) translateX(${-f*.12}px)`}}/>
    <NetworkBackdrop/>
    <BrandBug dark/>
    <div className="mesh-heading"><TypeLine text="No data?" delay={3}/><TypeLine text="Pass it on." delay={10} className="amber"/></div>
    <Enter className="mesh-tag" delay={18}><Icon name="bluetooth"/> Bluetooth relay</Enter>
    <Enter className="mesh-sub" delay={20}>Nearby compatible devices can help carry an SOS.</Enter>
    <div className="mesh-world" style={{transform:`translateX(${cameraPan}px) scale(${zoom})`}}>
    {[0,1,2].map(i=><RouteArc key={i} from={meshNodes[i]} to={meshNodes[i+1]} lift={110} progress={legs[i]} color={i===2?'#F2A540':'#65d7d3'} dashed/> )}
    {meshNodes.map((at,i)=><NodeBadge key={i} at={at} icon={meshIcons[i]} color={meshColors[i]} frame={f} delay={20+i*7} active={done[i]} pulseAt={[20,119,188,253][i]} size={114}/>)}
    <Character index={0} x={145} y={475} height={412} delay={14} travel={-120} lean={-5} active actionAt={60} actionEnd={119}/>
    <Character index={1} x={620} y={475} height={412} delay={37} travel={-80} lean={-5} active={done[1]} actionAt={119} actionEnd={188}/>
    <Character index={2} x={1093} y={475} height={412} delay={47} travel={90} lean={5} active={done[2]} actionAt={188}/>
    <Enter className="mesh-console" delay={52} x={70}>
      <div className="mesh-console-icon"><Icon name="monitor"/></div>
      <div className="mesh-console-lines"><i/><i/><i/></div>
      <div className="mesh-console-packet" style={{opacity:p(f,253,14),scale:.68+.32*pop(f,253)}}><Icon name="check"/></div>
    </Enter>
    {meshLabels.map((label,i)=><div key={i} className={`mesh-node-label ${done[i]?'reached':''}`} style={{left:meshNodes[i][0],opacity:p(f,35+i*5,15),transform:`translate(-50%,${(1-pop(f,35+i*5))*25}px)`}}>
      <b>{label}</b><span><i/>{done[i]?['SOS ready','Relaying','Alert reached','Possible uplink'][i]:['','Listening','In range','Awaiting alert'][i]}</span>
    </div>)}
    </div>
    <div className="mesh-foot"><Icon name="radio"/> Relay depends on participating devices within Bluetooth range and a reachable destination.</div>
    <div className="mesh-progress">{legs.map((v,i)=><i key={i}><em style={{width:`${v*100}%`}}/></i>)}</div>
  </Stage>;
}

function Dispatch() {
  const f=useCurrentFrame();
  const mapReveal=p(f,75,35), pin=p(f,104,20), dispatch=p(f,168,22), scale=1+.07*p(f,93,80);
  const statuses=[{name:'SOS received',icon:'check',time:38},{name:'Locate incident',icon:'pin',time:100},{name:'Coordinate response',icon:'arrow',time:168}] as const;
  return <Stage tone="light dispatch">
    <div className="dispatch-deco"/>
    <BrandBug/>
    <div className="dispatch-title">
      <Enter className="eyebrow" delay={3}><i/> COMMAND CENTER</Enter>
      <TypeLine text="From alert" delay={11}/>
      <TypeLine text="to action." delay={19} className="red"/>
    </div>
    <div className="dispatch-steps">{statuses.map(({name,icon,time},i)=>{
      const v=pop(f,time);
      return <div className="dispatch-step" key={name} style={{opacity:p(f,time,13),transform:`translateX(${(1-v)*-85}px)`}}>
        <div className="dispatch-step-icon"><Icon name={icon}/></div><div><small>0{i+1}</small><strong>{name}</strong></div>
      </div>;
    })}</div>
    <div className="browser" style={{left:776+(1-pop(f,8))*240,top:204+(1-pop(f,8))*190,scale:.86+.14*pop(f,8),opacity:p(f,8,18)}}>
      <div className="browser-bar"><i/><i/><i/><span>AGAP Command Center</span><b>Demo</b></div>
      <div className="browser-content">
        <Img src={art('product/command-overview.png')} alt="Actual AGAP Command Center overview demonstration"/>
        <div className="map-layer" style={{clipPath:`inset(0 ${100-100*mapReveal}% 0 0)`}}>
          <div className="map-zoom" style={{transform:`scale(${scale})`,transformOrigin:'46.67% 67.3%'}}>
            <Img src={art('product/command-map.png')} alt="Actual AGAP Command Center demo incident map"/>
            <div className="map-ping" style={{left:'46.67%',top:'67.3%',opacity:pin}}><RingBurst x={0} y={0} size={90} frame={f} delay={102} color="#176e7e" loop/><span><Icon name="pin"/></span></div>
            <div className="dispatch-highlight" style={{opacity:dispatch}}/>
          </div>
        </div>
      </div>
    </div>
    <Enter className="incident-toast" delay={36} x={62}>
      <div className="incident-toast-icon"><Icon name="cross"/></div><div><span>INCOMING ALERT</span><strong>Medical SOS</strong></div>
    </Enter>
    <div className="dispatch-ready" style={{opacity:p(f,175,18),transform:`translateY(${(1-pop(f,175))*50}px)`}}><Icon name="check"/> Response coordination</div>
    <div className="demo-foot">Demonstration data · response teams decide next steps.</div>
  </Stage>;
}

function Community() {
  const f=useCurrentFrame();
  const focus:Point=[960,394];
  const points:Point[]=[[390,665],[960,695],[1530,665]];
  return <Stage tone="light community">
    <div className="community-disc" style={{scale:.78+.22*pop(f,0)}}/>
    <div className="community-arc"/>
    <BrandBug/>
    <div className="community-heading"><TypeLine text="Every connection" delay={4}/><TypeLine text="counts." delay={15} className="red"/></div>
    {points.map((at,i)=><RouteArc key={i} from={at} to={focus} progress={ramp(f,51+i*13,101+i*13)} color={i===0?'#B93323':'#176e7e'} width={6} lift={25}/>)}
    <RingBurst x={960} y={394} frame={f} delay={105} size={212} color="#176e7e" loop/>
    <div className="community-shield" style={{left:960,top:394,opacity:p(f,89,20),scale:.35+.65*pop(f,89)}}><Img src={art('logos/agap-shield.png')} alt="AGAP shield"/></div>
    <Character index={0} x={240} y={461} height={500} delay={15} travel={-230} lean={-6} active actionAt={15} actionEnd={55}/>
    <Character index={1} x={835} y={497} height={475} delay={34} travel={-80} lean={-4} active actionAt={34} actionEnd={75}/>
    <Character index={2} x={1410} y={455} height={510} delay={48} travel={220} lean={7} active actionAt={48} actionEnd={88}/>
    {['Residents','Community','Responders'].map((label,i)=><div className="community-label" key={label} style={{left:points[i][0],opacity:p(f,44+i*9,14),transform:`translate(-50%,${(1-pop(f,44+i*9))*30}px)`}}><span/>{label}</div>)}
    <Enter className="community-caption" delay={111}>More ways to reach help.</Enter>
  </Stage>;
}

function Finale() {
  const f=useCurrentFrame(), shield=pop(f,12);
  const colors=['#B93323','#176e7e','#F2A540','#557918'];
  return <Stage tone="light finale">
    <div className="final-orbit" style={{transform:`translate(-50%,-50%) rotate(${f*.25}deg) scale(${.78+.22*pop(f,0)})`}}/>
    <RingBurst x={960} y={300} frame={f} delay={5} size={246} color="#176e7e"/>
    {colors.map((color,i)=>{
      const v=p(f,2+i*4,26);
      const a=i*Math.PI/2+Math.PI/4;
      const r=600*(1-v)+158;
      return <div className="final-particle" key={color} style={{left:960+Math.cos(a)*r,top:300+Math.sin(a)*r,background:color,opacity:p(f,2+i*4,9)*(1-p(f,31+i*3,12)),transform:`translate(-50%,-50%) rotate(${720*v+45*i}deg)`}}/>;
    })}
    <div className="final-shield" style={{scale:.38+.62*shield,opacity:p(f,12,18)}}><Img src={art('logos/agap-shield.png')} alt="AGAP shield"/></div>
    <div className="final-wordmark" style={{clipPath:`inset(0 ${100-100*p(f,30,31)}% 0 0)`,transform:`translateY(${(1-pop(f,30))*30}px)`}}><Wordmark/></div>
    <div className="final-title"><TypeLine text="Help is within reach." delay={57}/></div>
    <Enter className="final-cta" delay={79}>Get set up before an emergency.</Enter>
    <div className="final-colors">{colors.map((color,i)=><i key={color} style={{background:color,scale:`${p(f,89+i*4,14)} 1`}}/>)}</div>
  </Stage>;
}

/** Keep the previous cut's brand-color swipes. */
function Wipes() {
  const f=useCurrentFrame();
  return <AbsoluteFill className="wipes">
    {boundaries.slice(1,-1).map((s,i)=>{
      const delta=f-s*fps;
      if(delta < -15 || delta > 15) return null;
      const x=interpolate(delta,[-15,0,15],[-2500,-135,2250],{easing:Easing.inOut(Easing.cubic)});
      return <div key={s} className="wipe" style={{transform:`translateX(${x}px) skewX(-12deg)`,background:i%2===0?'var(--red)':'var(--teal)'}}><div/><div/></div>;
    })}
  </AbsoluteFill>;
}

const scenes=[
  {name:'No signal — storm hook',component:Hook},
  {name:'Motion SOS demonstration',component:SosDemo},
  {name:'Online direct connection',component:Online},
  {name:'Offline animated relay',component:Mesh},
  {name:'Command Center in action',component:Dispatch},
  {name:'Community connections',component:Community},
  {name:'AGAP brand resolution',component:Finale},
];
export function Explainer() {
  return <AbsoluteFill>
    {scenes.map(({name,component:Scene},i)=><Sequence name={name} key={name} from={boundaries[i]*fps} durationInFrames={(boundaries[i+1]-boundaries[i])*fps}><Scene/></Sequence>)}
    <Audio name="Original kinetic instrumental • 120 BPM" src={art('audio/agap-kinetic-instrumental.mp3')} volume={.95}/>
    <Wipes/>
  </AbsoluteFill>;
}
