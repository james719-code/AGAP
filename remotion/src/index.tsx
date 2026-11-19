import {Composition, registerRoot} from 'remotion';
import {Explainer, fps, durationInFrames} from './Explainer';
import {Story, storyFps, storyFrames} from './Story';
import './style.css';
import './story.css';

const Root = () => (
  <>
    <Composition id="AGAPExplainer" component={Story} width={1920} height={1080} fps={storyFps} durationInFrames={storyFrames}/>
    <Composition id="AGAPMotionExplainer" component={Explainer} width={1920} height={1080} fps={fps} durationInFrames={durationInFrames}/>
  </>
);

registerRoot(Root);
