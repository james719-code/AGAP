import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

// 48-second original electronic instrumental, 120 BPM. No voices or samples.
// Default: cinematic sprite story. --kinetic reproduces the earlier motion cut.
const root=fileURLToPath(new URL('..',import.meta.url));
const output=fileURLToPath(new URL('../../branding/audio/',import.meta.url));
const scratch=join(root,'out','audio-work');
mkdirSync(scratch,{recursive:true});
mkdirSync(output,{recursive:true});
const rate=48000, seconds=48, beat=.5, bar=2, tau=2*Math.PI;
const hz=n=>440*2**((n-69)/12);
const sin=(freq,t)=>Math.sin(tau*freq*t);
const decay=(t,a,d)=>Math.min(1,t/a)*Math.exp(-t*d);
const smooth=(t,a,b)=>{const v=Math.max(0,Math.min(1,(t-a)/(b-a)));return v*v*(3-2*v)};
const chords=[[45,52,55,60],[41,48,52,57],[48,55,59,64],[43,50,55,59],
  [45,52,57,60],[41,48,55,57],[48,55,60,64],[43,50,57,62]];
const bright=[[48,55,60,64],[43,50,55,62],[45,52,57,60],[41,48,53,60]];
const melody=[76,79,81,79,76,74,72,74,76,79,84,81,79,76,74,72];
const story=!process.argv.includes('--kinetic');
const hits=story?[6,12,17,25,31,37,43]:[6,14,19,29,37,43];
let seed=67919;
const noise=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/2147483648-1};
const pcm=Buffer.alloc(rate*seconds*4);
let prior=0;
for(let k=0;k<rate*seconds;k++){
  const t=k/rate, bi=Math.floor(t/beat), bt=t%beat, ei=Math.floor(t/(beat/2)), et=t%(beat/2);
  const barIndex=Math.floor(t/bar);
  const sequence=t<37?chords:bright;
  const notes=sequence[barIndex%sequence.length], old=sequence[(barIndex+sequence.length-1)%sequence.length];
  const chord=notes.map(hz), blend=smooth(t%bar,0,.22);
  const energy=story?(t<6?.46:t<12?.33:t<17?.58:t<25?.88:t<31?.77:t<37?1:t<43?.63:.45)
    :(t<6?.30:t<14?.75:t<19?.87:t<29?1:t<37?.85:t<43?.96:.47);
  const groove=smooth(t,5.6,6.25)*(1-smooth(t,43,44.3));
  const level=smooth(t,0,.45)*(1-smooth(t,46.8,48));
  const arpRoot=chord[[0,2,1,3,1,2,3,2][ei%8]]*2;
  const arp=(sin(arpRoot,et)+.33*sin(arpRoot*2,et)+.15*sin(arpRoot*3,et))*decay(et,.008,13);
  const melodyRoot=hz(melody[Math.floor(bi/2)%melody.length]);
  const mt=t%(beat*2);
  const lead=(sin(melodyRoot,mt)*.86+.16*sin(melodyRoot*2,mt)+.09*sin(melodyRoot*3,mt))*decay(mt,.035,3.1);
  const bassRoot=chord[0]/2;
  const bass=(sin(bassRoot,bt)+.44*sin(bassRoot*2,bt)+.13*sin(bassRoot*3,bt))*decay(bt,.012,3.8);
  const kick=Math.sin(tau*(47*bt+5.5*(1-Math.exp(-30*bt))))*decay(bt,.004,14);
  const n=noise(), hi=n-prior;prior=n;
  const hat=hi*decay(et,.003,eu(ei)?28:52)*(eu(ei)?1:.55);
  const clap=(bi%4===1||bi%4===3)?hi*decay(bt,.008,23)*(1+.24*Math.sin(tau*32*bt)):0;
  const cut=hits.reduce((a,b)=>Math.max(a,Math.exp(-(((t-b)/.10)**2))),0);
  const fill=hits.reduce((a,b)=>Math.max(a,(t>=b-.26&&t<b? smooth(t,b-.26,b):0)),0);
  for(let channel=0;channel<2;channel++){
    let pad=0;
    for(let i=0;i<4;i++){
      const drift=(channel?1:-1)*(.11+i*.04);
      const now=hz(notes[i])+drift, prev=hz(old[i])+drift;
      pad+=(sin(now,t)*blend+sin(prev,t)*(1-blend))*.82+.13*sin(now*2,t);
    }
    const duck=.76+.24*smooth(bt,0,.19)*groove;
    const sample=(pad*.024*duck+arp*.077*energy*(channel===0?.87:1)+lead*.068*(t<6?.45:.9)*(channel===1?.78:1)
      +bass*.074*energy*duck+kick*.12*groove+hat*.021*groove+clap*.033*groove
      +cut*sin(100,t)*.037+fill*hi*.009)*level;
    pcm.writeInt16LE(Math.round(Math.max(-.96,Math.min(.96,sample))*32767),(k*2+channel)*2);
  }
}
function eu(i){return i%2===1}
const raw=join(scratch,story?'story-score.raw':'kinetic-score.raw');
writeFileSync(raw,pcm);
execFileSync('ffmpeg',['-nostdin','-y','-hide_banner','-loglevel','error','-f','s16le','-ar',String(rate),'-ac','2',
  '-i',raw,'-af','loudnorm=I=-14:TP=-1.5:LRA=7','-ar',String(rate),'-b:a','192k',
  join(output,story?'agap-story-instrumental.mp3':'agap-kinetic-instrumental.mp3')],{stdio:'inherit'});
