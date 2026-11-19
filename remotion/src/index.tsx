import {Composition, registerRoot} from 'remotion';
import {Explainer, fps, durationInFrames} from './Explainer';
import './style.css';

const Root = () => (
  <Composition
    id="AGAPExplainer"
    component={Explainer}
    width={1920}
    height={1080}
    fps={fps}
    durationInFrames={durationInFrames}
  />
);

registerRoot(Root);
