import type {CSSProperties, ReactNode} from 'react';
import {Audio} from '@remotion/media';
import {AbsoluteFill, Easing, Img, Sequence, interpolate, spring, staticFile, useCurrentFrame} from 'remotion';
import {Icon, RouteArc, type Point} from './graphics';

export const storyFps=30;
export const storyFrames=48*storyFps;
const cuts=[0,6,12,17,25,31,37,43,48];
const frameRate=storyFps;
const base='illustrations/modern/story/';
const asset=(path:string)=>staticFile(path);
const art=(path:string)=>asset(base+path);
const city=asset('illustrations/modern/storm-city.png');
const ease={extrapolateLeft:'clamp',extrapolateRight:'clamp',easing:Easing.bezier(.22,1,.36,1)} as const;
const clamp={extrapolateLeft:'clamp',extrapolateRight:'clamp'} as const;
const reveal=(f:number,a:number,b:number)=>interpolate(f,[a,b],[0,1],ease);
const ramp=(f:number,a:number,b:number)=>interpolate(f,[a,b],[0,1],clamp);
const motion=(f:number,a:number,b:number,x:number,y:number)=>interpolate(f,[a,b],[x,y],clamp);
const pop=(f:number,start:number)=>spring({frame:Math.max(0,f-start),fps:frameRate,config:{mass:.72,damping:17,stiffness:175}});
type Rect=[number,number,number,number];
type Sheet={file:string;w:number;h:number;maxHeight:number;frames:Rect[];anchors?:number[];flip?:number[];correctedFile?:string;correctedPoses?:number[]};

// Alpha-checked individual crop bounds in row-major order. This uses the actual
// drawn sprites rather than interpolating a single standing illustration.
const sheets={
  run:{file:'resident-run.png',w:1536,h:1024,maxHeight:491,frames:[[46,26,285,461],[466,38,199,449],[852,30,219,457],[1178,20,331,407],[30,538,305,463],[466,550,195,451],[828,546,237,455],[1166,534,343,409]],anchors:[.53,.57,.52,.55,.55,.55,.55,.53]},
  sos:{file:'resident-sos.png',correctedFile:'resident-sos-eyeline.png',correctedPoses:[0,1,2,7],w:1536,h:1024,maxHeight:491,frames:[[98,10,245,491],[474,64,221,437],[812,120,331,379],[1206,104,251,395],[62,604,253,407],[442,602,253,409],[824,604,249,407],[1206,598,253,413]],anchors:[.5,.48,.37,.48,.47,.47,.46,.46]},
  volunteer:{file:'volunteer-walk.png',w:1536,h:1024,maxHeight:487,frames:[[80,10,235,477],[488,10,165,477],[840,10,241,475],[1240,10,199,477],[94,526,187,483],[486,522,181,487],[870,528,179,481],[1200,526,263,481]],anchors:[.48,.54,.47,.52,.5,.49,.49,.51]},
  medicRun:{file:'responder-run.png',w:1536,h:1024,maxHeight:469,frames:[[68,24,297,465],[482,38,263,447],[854,34,269,451],[1230,24,273,457],[28,532,343,469],[462,544,287,455],[834,536,289,465],[1152,528,373,415]],anchors:[.47,.47,.47,.45,.47,.47,.47,.49]},
  // All poses share the same scale, planted near foot and seated ground line.
  // The third pose looks left at the resident; the fourth looks to the responder.
  patient:{file:'patient-continuity-v2.png',w:1278,h:1230,maxHeight:587,frames:[[52,38,553,557],[653,30,597,567],[48,631,543,583],[687,629,549,587]],anchors:[.353,.412,.372,.375]},
  medicAid:{file:'responder-aid.png',w:1218,h:1292,maxHeight:645,frames:[[136,10,319,645],[641,118,431,539],[100,749,429,517],[689,733,419,533]],anchors:[.48,.47,.58,.58],flip:[0,1]},
  reaction:{file:'resident-reaction-eyeline.png',w:1536,h:1024,maxHeight:491,frames:[[180,8,465,483],[898,8,467,483],[168,516,465,489],[900,518,469,487]],anchors:[.50,.49,.50,.50]},
} satisfies Record<string,Sheet>;
type SheetName=keyof typeof sheets;

function stepFrame(frame:number, rate:number, frames:number[], start=0) {
  return frames[Math.floor(Math.max(0,frame-start)*rate/frameRate)%frames.length];
}
function actionFrame(f:number,cues:[number,number][]) {
  let index=cues[0][1];
  for(const [at,pose] of cues)if(f>=at)index=pose;
  return index;
}

function Sprite({sheet,pose,x,ground,height,opacity=1,lift=0,shadow=true,scaleX=1}:{
  sheet:SheetName;pose:number;x:number;ground:number;height:number;opacity?:number;lift?:number;shadow?:boolean;scaleX?:number;
}) {
  const info:Sheet=sheets[sheet], region=info.frames[pose%info.frames.length], [sx,sy,w,h]=region;
  const s=height/info.maxHeight;
  const anchor=(info.anchors?.[pose]??.5), flip=info.flip?.includes(pose)??false;
  const width=w*s, frameHeight=h*s, anchorX=anchor*width, foot=ground-lift;
  // The facial correction is composited with the original sprite's alpha,
  // retaining the exact same body silhouette, pose alignment and clear background.
  const corrected=!!info.correctedFile&&!!info.correctedPoses?.includes(pose);
  const source=corrected?info.correctedFile!:info.file;
  const silhouette=corrected?{
    maskImage:`url("${art(info.file)}")`,maskSize:'100% 100%',maskRepeat:'no-repeat',maskMode:'alpha' as const,
  }:{};
  return <>
    {shadow&&<div className="story-contact-shadow" style={{left:x,top:ground-4,width:Math.max(70,width*.65),opacity:opacity*(1-lift/130)}}/>}
    <div className="story-sprite" style={{left:x-anchorX,top:foot-frameHeight,width,height:frameHeight,opacity,transform:`scaleX(${(flip?-1:1)*scaleX})`,transformOrigin:`${anchorX}px bottom`}}>
      <Img src={art(source)} alt="" draggable={false} style={{width:info.w*s,height:info.h*s,left:-sx*s,top:-sy*s,...silhouette}}/>
    </div>
  </>;
}

// Keep the patient's proportions constant against the other characters in every
// shot; the only size changes are camera framing applied to the whole cast.
function Patient({pose,x,ground,standingHeight}:{pose:number;x:number;ground:number;standingHeight:number}) {
  return <Sprite sheet="patient" pose={pose} x={x} ground={ground} height={standingHeight*.66}/>;
}

function Shot({children,light=false}:{children:ReactNode;light?:boolean}) {
  return <AbsoluteFill className={`story-stage ${light?'story-light':''}`}>{children}</AbsoluteFill>;
}
function Caption({children,start=8,end=Infinity,sub}:{children:ReactNode;start?:number;end?:number;sub?:string}) {
  const f=useCurrentFrame(), alpha=reveal(f,start,start+12)*(Number.isFinite(end)?1-ramp(f,end-12,end):1);
  return <div className="story-caption" style={{opacity:alpha,transform:`translate(-50%,${(1-alpha)*14}px)`}}><b>{children}</b>{sub&&<small>{sub}</small>}</div>;
}
function Mark({white=false}:{white?:boolean}) {
  return <div className={`story-mark ${white?'story-mark-white':''}`}><Img src={asset('logos/agap-wordmark.png')} alt="AGAP PH"/></div>;
}
function Rain({amount=1,wind=1,front=false}:{amount?:number;wind?:number;front?:boolean}) {
  const f=useCurrentFrame(), n=front?65:130;
  return <svg className={`story-rain ${front?'front':''}`} viewBox="0 0 1920 1080" style={{opacity:amount*(front?.54:.36)}}>
    {Array.from({length:n},(_,i)=>{
      const x=((i*193+123-f*(3+i%4)*wind)%2090+2090)%2090-80;
      const y=(i*87+f*(17+i%9))%1240-80, len=17+i%7*7;
      return <line key={i} x1={x} y1={y} x2={x-12*wind} y2={y+len} stroke={i%4===0?'#E6F6FC':'#8EBAC8'} strokeWidth={front?2.3:1.6} opacity={.24+(i%5)*.12}/>;
    })}
  </svg>;
}
function Street({zoom=1.08,pan=0,bright=0,rain=1,background=city}:{zoom?:number;pan?:number;bright?:number;rain?:number;background?:string}) {
  return <>
    <Img className="story-street" src={background} alt="" style={{transform:`translateX(${pan}px) scale(${zoom})`,filter:`brightness(${.83+.15*bright}) saturate(${.96+.06*bright})`}}/>
    <div className="story-vignette" style={{opacity:1-bright*.5}}/>
    <div className="story-ambient" style={{opacity:bright*.18}}/>
    <Rain amount={rain}/>
  </>;
}
function Letterbox() {return <><div className="story-bar story-bar-top"/><div className="story-bar story-bar-bottom"/></>}
function ReactionCut({pose,local}:{pose:number;local:number}) {
  return <div className="story-reaction-cut">
    <Img src={city} alt="" className="story-reaction-back"/>
    <div className="story-reaction-shade"/>
    <Sprite sheet="reaction" pose={pose} x={1048-18*local/30} ground={1083} height={930+20*local/30} shadow={false}/>
    <Rain front amount={.65}/>
  </div>;
}
function Glow({at,start=0,frame,color='#F2A540',size=100,repeat=false}:{at:Point;start?:number;frame:number;color?:string;size?:number;repeat?:boolean}) {
  return <div className="story-glow" style={{left:at[0],top:at[1],width:size,height:size,color}}>
    {[0,1,2].map(i=>{const age=frame-start-i*9,v=age<0?0:repeat?(age%48)/48:Math.min(1,age/48);return <i key={i} style={{scale:.55+v*1.8,opacity:age<0?0:(1-v)*.75}}/>})}
  </div>;
}
function ContactSplash({x,ground,frame,active=1}:{x:number;ground:number;frame:number;active?:number}) {
  const cycle=frame%9;
  return <svg className="story-splash" viewBox="0 0 1920 1080" style={{opacity:active*(1-cycle/9)*.75}}>
    {[0,1,2,3,4,5].map(i=><line key={i} x1={x+(i-2.5)*18} y1={ground-2} x2={x+(i-2.5)*(18+cycle*7)} y2={ground-9-cycle*(1+i%3)*3} stroke="#b6e3e5" strokeWidth={3.3} strokeLinecap="round"/>)}
  </svg>;
}
function AlertChip({left,top,icon,title,detail,visible=1,color='teal'}:{
  left:number;top:number;icon:'signal'|'wifi'|'cross'|'check'|'bluetooth'|'pin'|'radio'|'monitor';title:string;detail?:string;visible?:number;color?:'red'|'teal'|'amber';
}) {
  return <div className={`story-chip ${color}`} style={{left,top,opacity:visible,transform:`translateY(${(1-visible)*24}px)`}}><div className="story-chip-icon"><Icon name={icon}/></div><div><strong>{title}</strong>{detail&&<small>{detail}</small>}</div></div>;
}

/* Scene 1: an establishing shot followed by a genuine eight-drawing run loop. */
function Encounter() {
  const f=useCurrentFrame(), runUntil=145, runStart=30;
  const x=motion(f,runStart,runUntil,-155,1250), ground=854;
  const runPose=stepFrame(f,12,[0,1,2,3,4,5,6,7],runStart);
  const airborne=runPose===3||runPose===7?18:0;
  const camera=reveal(f,105,180);
  return <Shot>
    <Street zoom={1.095+.055*reveal(f,0,180)} pan={-40*reveal(f,0,180)}/>
    <div className="story-actors" style={{transform:`translateX(${-18*camera}px) scale(${1+.06*camera})`,transformOrigin:'1320px 825px'}}>
      <Patient pose={f<95?0:1} x={1515} ground={ground+5} standingHeight={415}/>
      {f<runUntil?<><Sprite sheet="run" pose={runPose} x={x} ground={ground} height={415} lift={airborne}/><ContactSplash x={x-24} ground={ground} frame={f} active={f>runStart?1:0}/></>:
        <Sprite sheet="sos" pose={0} x={1250} ground={ground} height={415}/>}
    </div>
    <Rain front amount={.9}/>
    <Glow at={[1510,480]} start={82} frame={f} color="#F2A540" size={45}/>
    <div className="story-emergency-sign" style={{left:1510,top:480,opacity:reveal(f,76,92)*(1-ramp(f,148,171)),scale:.6+.4*pop(f,76)}}><Icon name="alert"/></div>
    {f>=20&&f<44&&<ReactionCut pose={f<28?0:1} local={f-20}/>}
    <Mark white/>
    <div className="story-opening-fade" style={{opacity:1-ramp(f,0,22)}}/>
    <Letterbox/>
  </Shot>;
}

/* Scene 2: the same two people in a closer angle, eight one-shot acting poses. */
function Emergency() {
  const f=useCurrentFrame();
  const resident=actionFrame(f,[[0,0],[12,1],[32,2],[58,3],[78,4],[110,5],[124,6],[153,7]]);
  const patient=actionFrame(f,[[0,1],[32,2]]);
  const signalLost=reveal(f,91,116);
  return <Shot>
    <Street zoom={1.24+.07*reveal(f,0,180)} pan={-128-60*reveal(f,0,180)} rain={.92}/>
    <div className="story-street-ground-shadow"/>
    <Patient pose={patient} x={1270} ground={930} standingHeight={615}/>
    <Sprite sheet="sos" pose={resident} x={838} ground={929} height={615}/>
    <Rain front amount={.65}/>
    {f>=106&&f<143&&<ReactionCut pose={f<126?2:3} local={f-106}/>}
    <AlertChip left={115} top={214} icon={signalLost>.5?'signal':'wifi'} title={signalLost>.5?'No mobile data':'Checking connection'} detail={signalLost>.5?'Network unavailable':'Searching…'} visible={reveal(f,79,99)} color={signalLost>.5?'red':'teal'}/>
    <Glow at={[350,401]} start={103} frame={f} color="#F2A540" size={75}/>
    <div className="story-signal-stack" style={{opacity:reveal(f,80,101)}}>
      {[0,1,2,3,4].map(i=><i key={i} style={{height:10+i*19,opacity:1-ramp(f,92+(4-i)*4,101+(4-i)*4)}}/>)}
      <span style={{opacity:signalLost,scale:.5+.5*pop(f,100)}}><Icon name="signal"/></span>
    </div>
    <Letterbox/>
  </Shot>;
}

/* Scene 3: illustrated close-up of her hand holding the actual AGAP interface. */
function PhoneInsert() {
  const f=useCurrentFrame();
  const drag=reveal(f,43,85), confirmed=reveal(f,92,104), send=reveal(f,111,126);
  const handScale=.96, hx=490, hy=61;
  const screen={left:hx+265*handScale,top:hy+67*handScale,width:450*handScale,height:923*handScale};
  const camera=.93+.07*pop(f,2)+.015*reveal(f,82,148);
  const fingertipX=screen.left+screen.width*.5, fingertipY=screen.top+screen.height*(.466-.1165*drag);
  // The life-size fingertip is the control point, not an undersized entire hand.
  // It enters from off-camera, touches the control, tracks it exactly, then lifts.
  const handEnter=reveal(f,19,42), handLeave=reveal(f,91,108), fingerScale=.55;
  const contact=reveal(f,40,43)*(1-confirmed);
  return <Shot>
    <Img className="story-phone-back" src={city} alt=""/>
    <div className="story-phone-shade"/>
    <div className="story-phone-group" style={{transform:`translateY(${(1-pop(f,2))*100}px) scale(${camera})`,transformOrigin:'960px 530px'}}>
      <Img className="story-hand" src={art('phone-hand.png')} alt="Illustration of a hand holding a smartphone" style={{left:hx,top:hy,width:1086*handScale,height:1448*handScale}}/>
      <div className="story-phone-screen" style={screen}>
        <Img src={asset('product/citizen-home.png')} alt="AGAP app's actual emergency SOS screen"/>
        <div className="story-original-sos-mask" style={{opacity:contact}}/>
        <div className="story-touch-track" style={{height:`${11.65*drag}%`,opacity:contact}}/>
        <div className="story-touch-puck" style={{top:`${46.6-11.65*drag}%`,opacity:contact,scale:1-.16*drag}}><b>SOS</b><small>DRAG</small></div>
        <div className="story-touch-ring" style={{top:`${46.6-11.65*drag}%`,opacity:contact,scale:1+.08*Math.sin(f/6)}}/>
        <div className="story-touch-check" style={{opacity:confirmed,scale:.62+.38*pop(f,92)}}><Icon name="check"/></div>
      </div>
      <Img className="story-tap-hand" src={art('tap-finger-closeup.png')} alt="" style={{left:fingertipX-74*fingerScale+1080*(1-handEnter)+1150*handLeave,top:fingertipY-146*fingerScale+130*(1-handEnter)+170*handLeave,width:1942*fingerScale,height:809*fingerScale,transformOrigin:`${74*fingerScale}px ${146*fingerScale}px`,transform:'rotate(20deg)'}}/>
    </div>
    <AlertChip left={1245} top={344} icon="cross" title="Medical SOS" detail="Location when available" visible={send} color="red"/>
    <Glow at={[1390,414]} start={109} frame={f} color="#F2A540" size={125}/>
    <div className="story-send-packet" style={{left:motion(f,120,149,1390,1980),top:414-55*Math.sin(ramp(f,120,149)*Math.PI),opacity:reveal(f,114,125)}}><Icon name="radio"/></div>
    <Caption start={12} end={91}>Drag toward the help you need.</Caption>
    <Caption start={92}>Release to create an SOS.</Caption>
    <Letterbox/>
  </Shot>;
}

/* Scene 4: the volunteer walks while a visible packet travels between devices. */
function Relay() {
  const f=useCurrentFrame();
  const vWalk=f<94||f>=140;
  const vX=f<94?motion(f,0,94,750,1045):f<140?1045:motion(f,140,240,1045,1380);
  const vp=f<94?stepFrame(f,9,[0,1,2,3]):f<140?actionFrame(f,[[94,4],[106,5],[122,6]]):stepFrame(f,9,[7,1,2,3],140);
  const first=ramp(f,51,101), second=ramp(f,150,211);
  const source:Point=[330,490], peer:Point=[vX+45,420], gateway:Point=[1700,270];
  return <Shot>
    <Street zoom={1.1+.055*reveal(f,0,240)} pan={34-85*reveal(f,0,240)} rain={.78}/>
    <Patient pose={2} x={565} ground={905} standingHeight={430}/>
    <Sprite sheet="sos" pose={f<48?6:7} x={288} ground={904} height={430}/>
    <Sprite sheet="volunteer" pose={vp} x={vX} ground={907} height={430} lift={vWalk&&vp===3?8:0}/>
    {vWalk&&<ContactSplash x={vX-16} ground={907} frame={f} active={.6}/>}
    <RouteArc from={source} to={peer} lift={115} progress={first} color="#65d7d3" width={8} dashed/>
    <RouteArc from={peer} to={gateway} lift={106} progress={second} color="#F2A540" width={8} dashed/>
    <Glow at={source} start={43} frame={f} color="#F2A540" size={66} repeat/>
    <Glow at={peer} start={101} frame={f} color="#65d7d3" size={110} repeat/>
    <Glow at={gateway} start={210} frame={f} color="#F2A540" size={110} repeat/>
    <div className="story-relay-node red" style={{left:source[0],top:source[1],opacity:reveal(f,33,51)}}><Icon name="radio"/></div>
    <div className="story-relay-node" style={{left:peer[0],top:peer[1],opacity:reveal(f,44,61),scale:first>=1?1+.09*Math.sin(f/6):.85}}><Icon name="bluetooth"/></div>
    <div className="story-relay-node amber" style={{left:gateway[0],top:gateway[1],opacity:reveal(f,140,157),scale:.8+.2*pop(f,140)}}><Icon name="wifi"/></div>
    <div className="story-relay-label" style={{left:peer[0],top:peer[1]-70,opacity:reveal(f,102,117)*(1-ramp(f,181,210))}}>Relaying SOS</div>
    <div className="story-relay-label" style={{left:gateway[0],top:gateway[1]-70,opacity:reveal(f,206,222)}}>Connected gateway</div>
    <Rain front amount={.51}/>
    <Caption start={56} end={240} sub="Illustrative relay · participating devices must be within range.">Nearby devices can carry the SOS toward a connection.</Caption>
    <Letterbox/>
  </Shot>;
}

/* Scene 5: arrive at the desk, zoom into real Command Center UI and map. */
function ControlRoom() {
  const f=useCurrentFrame();
  const zoom=reveal(f,24,91), map=reveal(f,80,111), arrived=reveal(f,17,34), dispatch=reveal(f,127,145);
  const scale=1+.73*zoom, pan=-252*zoom;
  return <Shot>
    <div className="story-room" style={{transform:`translateX(${pan}px) scale(${scale})`,transformOrigin:'1225px 510px'}}>
      <Img className="story-room-plate" src={art('command-room.png')} alt="Pixel-art emergency operations room"/>
      <div className="story-monitor" style={{left:812,top:278,width:826,height:455}}>
        <div className="story-monitor-ui">
          <Img src={asset('product/command-overview.png')} alt="AGAP Command Center overview, demo data"/>
          <Img src={asset('product/command-map.png')} alt="AGAP map, demo data" style={{clipPath:`inset(0 ${100*(1-map)}% 0 0)`}}/>
          <div className="story-monitor-map-pin" style={{opacity:reveal(f,100,119),scale:.64+.36*pop(f,100)}}><Icon name="pin"/></div>
          <div className="story-dispatch-highlight" style={{opacity:dispatch}}/>
        </div>
        <div className="story-monitor-toast" style={{opacity:arrived,transform:`translateY(${(1-pop(f,17))*24}px)`}}><Icon name="cross"/><div><small>INCOMING ALERT</small><b>Medical SOS</b></div></div>
        <div className="story-monitor-status" style={{opacity:dispatch}}><Icon name="check"/> Response coordination</div>
      </div>
    </div>
    <div className="story-control-ambient"/>
    <Glow at={[1223,331]} frame={f} start={17} color="#F2A540" size={94}/>
    <Caption start={108} sub="Demonstration interface.">Response teams coordinate the next steps.</Caption>
    <Letterbox/>
  </Shot>;
}

/* Scene 6: run cycle played forward at 12 drawings/sec with real travel. */
function ResponseRun() {
  const f=useCurrentFrame(), start=16;
  const x=motion(f,start,180,2070,-160);
  const pose=stepFrame(f,12,[0,1,2,3,4,5,6,7],start);
  const lift=pose===3||pose===7?23:0;
  const movement=ramp(f,start,180);
  return <Shot>
    <Street zoom={1.2+.08*movement} pan={-100+190*movement} rain={.92} background={art('response-street.png')}/>
    <div className="story-response-glow" style={{opacity:.15+.09*Math.sin(f/7)}}/>
    <div className="story-speed-lines" style={{opacity:reveal(f,15,32)*(1-ramp(f,151,170))}}>
      {Array.from({length:12},(_,i)=><i key={i} style={{top:139+(i*83)%783,left:(i*363+f*44)%2190-180,width:80+(i%4)*38,opacity:.13+(i%4)*.13}}/>)}
    </div>
    <Sprite sheet="medicRun" pose={pose} x={x} ground={915} height={640} lift={lift}/>
    <ContactSplash x={x+24} ground={915} frame={f} active={.9}/>
    <Rain front amount={1}/>
    <AlertChip left={1190} top={135} icon="pin" title="Incident assigned" detail="Illustrative response" color="teal" visible={reveal(f,4,22)*(1-ramp(f,68,92))}/>
    <Caption start={31}>Help is on the move.</Caption>
    <Letterbox/>
  </Shot>;
}

/* Scene 7: the responder arrives, lowers to a knee, opens the kit and reassures. */
function Assistance() {
  const f=useCurrentFrame(), warm=reveal(f,92,172);
  const pose=actionFrame(f,[[0,0],[44,0],[61,1],[91,2],[130,3]]);
  const rx=f<44?motion(f,0,44,1980,1515):1515;
  const patient=actionFrame(f,[[0,2],[111,3]]);
  const resident=actionFrame(f,[[0,7],[52,2],[108,7]]);
  return <Shot>
    <Street zoom={1.18+.15*warm} pan={-95-55*warm} bright={warm} rain={.72-.49*warm}/>
    <div className="story-actors" style={{transform:`scale(${1+.12*warm})`,transformOrigin:'1080px 929px'}}>
    <Patient pose={patient} x={1063} ground={929} standingHeight={565}/>
    <Sprite sheet="sos" pose={resident} x={665} ground={932} height={565}/>
    {f<44?<><Sprite sheet="medicRun" pose={stepFrame(f,12,[0,1,2,3,4,5,6,7])} x={rx} ground={932} height={571}/><ContactSplash x={rx} ground={932} frame={f}/></>:
      <Sprite sheet="medicAid" pose={pose} x={rx} ground={932} height={571}/>}
    </div>
    <Rain front amount={.55*(1-warm)}/>
    <div className="story-relief" style={{opacity:.18*warm}}/>
    <Caption start={112}>Every connection can make a difference.</Caption>
    <Letterbox/>
  </Shot>;
}

function StoryEnd() {
  const f=useCurrentFrame();
  const shield=pop(f,10), line=reveal(f,39,65), title=reveal(f,56,79);
  return <Shot light>
    <div className="story-end-orbit" style={{scale:.75+.25*pop(f,0),rotate:f*.6+'deg'}}/>
    <Glow at={[960,318]} frame={f} start={8} color="#176e7e" size={210}/>
    {['#B93323','#176E7E','#F2A540','#557918'].map((c,i)=>{
      const q=reveal(f,4+i*4,32+i*4),a=Math.PI/4+i*Math.PI/2,r=670*(1-q)+145;
      return <i key={c} className="story-end-block" style={{left:960+Math.cos(a)*r,top:318+Math.sin(a)*r,background:c,opacity:reveal(f,4+i*4,13+i*4)*(1-ramp(f,32+i*4,48+i*4)),rotate:720*q+'deg'}}/>;
    })}
    <div className="story-end-shield" style={{scale:.42+.58*shield,opacity:reveal(f,10,25)}}><Img src={asset('logos/agap-shield.png')} alt="AGAP shield"/></div>
    <div className="story-end-mark" style={{clipPath:`inset(0 ${100-100*line}% 0 0)`}}><Img src={asset('logos/agap-wordmark.png')} alt="AGAP PH"/></div>
    <div className="story-end-title" style={{opacity:title,transform:`translateY(${(1-title)*45}px)`}}>Help is within reach.</div>
    <div className="story-end-sub" style={{opacity:reveal(f,78,100)}}>Get set up before an emergency.</div>
    <div className="story-end-bars">{['#B93323','#176E7E','#F2A540','#557918'].map((c,i)=><i key={c} style={{background:c,scale:`${reveal(f,93+i*3,111+i*3)} 1`}}/>)}</div>
  </Shot>;
}

// The signature AGAP angled color swipes at chapter breaks. Match cuts at 6s
// and 37s intentionally stay inside the same action to preserve continuity.
function StoryWipes() {
  const f=useCurrentFrame();
  return <AbsoluteFill className="wipes">
    {[12,17,25,31,43].map((s,i)=>{
      const d=f-s*frameRate;if(d<-15||d>15)return null;
      const x=interpolate(d,[-15,0,15],[-2500,-135,2250],{easing:Easing.inOut(Easing.cubic)});
      return <div className="wipe" key={s} style={{transform:`translateX(${x}px) skewX(-12deg)`,background:i%2===0?'var(--red)':'var(--teal)'}}><div/><div/></div>;
    })}
  </AbsoluteFill>;
}

const scenes=[
  {name:'01 · A rainy evening',component:Encounter},
  {name:'02 · Someone needs help',component:Emergency},
  {name:'03 · Create an SOS',component:PhoneInsert},
  {name:'04 · The alert travels',component:Relay},
  {name:'05 · Command Center',component:ControlRoom},
  {name:'06 · Responding to the call',component:ResponseRun},
  {name:'07 · The responder arrives',component:Assistance},
  {name:'08 · AGAP',component:StoryEnd},
];

export function Story() {
  return <AbsoluteFill>
    {scenes.map(({name,component:Scene},i)=><Sequence key={name} name={name} from={cuts[i]*frameRate} durationInFrames={(cuts[i+1]-cuts[i])*frameRate}><Scene/></Sequence>)}
    <Audio name="AGAP story instrumental · 120 BPM" src={asset('audio/agap-story-instrumental.mp3')} volume={.95}/>
    <StoryWipes/>
  </AbsoluteFill>;
}
