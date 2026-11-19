import {Audio} from '@remotion/media';
import type {ReactNode} from 'react';
import {
  AbsoluteFill, Easing, Img, Interactive, Sequence, interpolate, staticFile,
  useCurrentFrame, useVideoConfig,
} from 'remotion';
import captions from './captions.json';
import {Icon, Network, Topography} from './graphics';

export const fps = 30;
export const durationInFrames = 60 * fps;

const reveal = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic)} as const;

function Enter({children, className, delay = 0}: {children: ReactNode; className?: string; delay?: number}) {
  const frame = useCurrentFrame();
  const progress = interpolate(frame, [delay, delay + 18], [0, 1], reveal);
  return <div className={className} style={{opacity: progress, translate: `0 ${(1 - progress) * 36}px`}}>{children}</div>;
}

function Stage({children, label, tone = ''}: {children: ReactNode; label: string; tone?: string}) {
  return (
    <AbsoluteFill className={`stage ${tone}`}>
      <Topography />
      <header className="masthead">
        <div className="signature"><Img src={staticFile('agap-mark.png')} /> <span>AGAP</span></div>
        <div className="scene-label">{label}</div>
      </header>
      {children}
    </AbsoluteFill>
  );
}

function Opening() {
  return (
    <Stage label="Emergency response">
      <Enter className="copy opening-copy" delay={3}>
        <Interactive.Div name="Opening headline" className="headline">
          When the<br />signal <em>drops.</em>
        </Interactive.Div>
        <Interactive.Div name="Opening subtitle" className="dek">An emergency still needs a way to reach help.</Interactive.Div>
      </Enter>
      <Enter className="opening-visual" delay={8}>
        <div className="illustration-frame">
          <Img className="illustration" src={staticFile('offline-illustration.png')} />
        </div>
        <div className="outage-label"><Icon name="signal" /> <span>Mobile data unavailable</span></div>
      </Enter>
    </Stage>
  );
}

function Report() {
  const time = useCurrentFrame() / useVideoConfig().fps;
  const pulse = (time * 0.85) % 1;

  return (
    <Stage label="Sending an SOS" tone="paper">
      <Enter className="copy report-copy">
        <Interactive.Div name="SOS headline" className="headline">Ask for help.<br /><em>Share where.</em></Interactive.Div>
        <Interactive.Div name="SOS instructions" className="dek">Drag SOS toward the emergency type and release to send.</Interactive.Div>
        <div className="fact-pair">
          <span><Icon name="cross" /> Emergency type</span>
          <span><Icon name="pin" /> Location when available</span>
        </div>
      </Enter>
      <Enter className="report-visual" delay={5}>
        <div className="orbit orbit-inner" />
        <div className="orbit orbit-outer" />
        <div className="handset">
          <Img src={staticFile('citizen-home.png')} alt="AGAP Android SOS screen" />
          <div className="sos-pulse" style={{scale: 1 + pulse * 1.25, opacity: 0.75 * (1 - pulse)}} />
        </div>
      </Enter>
    </Stage>
  );
}

function Delivery() {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const online = frame < 4.1 * fps;

  return (
    <Stage label="How the alert travels">
      <Enter className="network-heading">
        <Interactive.Div name="Delivery headline" className="headline">Two ways for an SOS to travel.</Interactive.Div>
      </Enter>
      <Enter className="network-placement" delay={4}><Network online={online} /></Enter>
      <div className="network-footnote">
        {online ? 'With a connection, AGAP sends the alert online.' : 'Offline delivery depends on compatible devices within Bluetooth range.'}
      </div>
    </Stage>
  );
}

function Dispatch() {
  return (
    <Stage label="Coordinating a response" tone="paper">
      <Enter className="copy dispatch-copy">
        <Interactive.Div name="Dispatch headline" className="headline">See where<br /><em>help is needed.</em></Interactive.Div>
        <Interactive.Div name="Dispatch subtitle" className="dek">Response teams can map reports and decide who to send.</Interactive.Div>
        <Enter className="status" delay={125}>
          <span><Icon name="check" /> SOS received</span>
          <span className="status-arrow">→</span>
          <span><Icon name="arrow" /> Help en route</span>
        </Enter>
      </Enter>
      <Enter className="dispatch-visual" delay={7}>
        <Img src={staticFile('dispatch-preview.png')} alt="AGAP Command Center dispatch dashboard" />
        <div className="detail-label">AGAP Command Center · Demo view</div>
      </Enter>
    </Stage>
  );
}

function BenefitCard({title, description, icon, cue, color}: {
  title: string; description: string; icon: 'person' | 'bluetooth' | 'cross'; cue: number; color: string;
}) {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const active = interpolate(frame, [cue * fps, (cue + 0.42) * fps], [0, 1], reveal);

  return (
    <div className={`benefit-card ${color}`} style={{translate: `0 ${(1 - active) * 14}px`}}>
      <div className="benefit-bracket" style={{opacity: 0.3 + active * 0.7}} />
      <div className="benefit-icon"><Icon name={icon} /></div>
      <div className="benefit-name">{title}</div>
      <div className="benefit-description">{description}</div>
    </div>
  );
}

function Community() {
  return (
    <Stage label="Who AGAP helps">
      <Enter className="community-heading">
        <Interactive.Div name="Community headline" className="headline">How a community <em>helps.</em></Interactive.Div>
      </Enter>
      <Enter className="benefits" delay={5}>
        <BenefitCard title="Residents" description="Another way to ask for help when mobile data is down." icon="person" cue={0.4} color="apricot" />
        <BenefitCard title="Neighbors" description="Nearby phones can pass along an alert in the background." icon="bluetooth" cue={3.55} color="aqua" />
        <BenefitCard title="Response teams" description="A clearer picture of what happened and where to act." icon="cross" cue={6.35} color="lime" />
      </Enter>
    </Stage>
  );
}

function Closing() {
  return (
    <AbsoluteFill className="stage paper closing">
      <Topography />
      <Enter className="close-brand">
        <Img className="close-mark" src={staticFile('agap-mark.png')} />
        <div className="close-wordmark"><Img src={staticFile('agap-wordmark.png')} alt="AGAP PH" /></div>
      </Enter>
      <Enter className="close-text" delay={9}>
        <Interactive.Div name="Closing headline" className="headline">Get set up before an emergency.</Interactive.Div>
        <div className="close-full-name">Accelerated Geolocation-based Assistance and Protection</div>
      </Enter>
      <Enter className="credit" delay={24}>
        <strong>The Fellowship · Traveling Salesman</strong>
        <span>Heroes of Innovation Challenge 2026 · Ibalong Festival, Legazpi City</span>
      </Enter>
    </AbsoluteFill>
  );
}

function Captions() {
  const time = useCurrentFrame() / useVideoConfig().fps;
  const cue = captions.find(({start, end}) => time >= start && time < end);
  return cue ? <div className="caption"><span>{cue.text}</span></div> : null;
}

export function Explainer() {
  const {fps} = useVideoConfig();
  return (
    <AbsoluteFill>
      <Sequence name="When the signal drops" from={0} durationInFrames={8 * fps}><Opening /></Sequence>
      <Sequence name="Send an SOS" from={8 * fps} durationInFrames={10 * fps}><Report /></Sequence>
      <Sequence name="Internet and Bluetooth routes" from={18 * fps} durationInFrames={14 * fps}><Delivery /></Sequence>
      <Sequence name="Responder coordination" from={32 * fps} durationInFrames={10 * fps}><Dispatch /></Sequence>
      <Sequence name="Who AGAP helps" from={42 * fps} durationInFrames={10 * fps}><Community /></Sequence>
      <Sequence name="Project credits" from={52 * fps} durationInFrames={8 * fps}><Closing /></Sequence>
      <Audio name="English narration" src={staticFile('narration.mp3')} />
      <Audio name="Original backing score" src={staticFile('score.mp3')} volume={0.72} />
      <Captions />
    </AbsoluteFill>
  );
}
