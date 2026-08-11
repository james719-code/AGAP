class AudioAlertSynthesizer {
  private ctx: AudioContext | null = null;
  private isEnabled: boolean = false;

  constructor() {
    // Lazy AudioContext initialization on first user interaction or trigger
  }

  public setEnabled(enabled: boolean) {
    this.isEnabled = enabled;
  }

  public getEnabled(): boolean {
    return this.isEnabled;
  }

  private async initCtx(): Promise<boolean> {
    if (!this.ctx) {
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      await this.ctx.resume();
    }
    return this.ctx?.state === 'running';
  }

  /**
   * Triggers a short dual-tone emergency siren sound
   */
  public async playEmergencySiren(): Promise<boolean> {
    if (!this.isEnabled) return false;
    try {
      const ready = await this.initCtx();
      if (!this.ctx || !ready) return false;

      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();

      osc.type = 'sawtooth';
      const now = this.ctx.currentTime;

      // Frequency sweep from 700Hz to 1100Hz and back
      osc.frequency.setValueAtTime(700, now);
      osc.frequency.exponentialRampToValueAtTime(1100, now + 0.2);
      osc.frequency.exponentialRampToValueAtTime(700, now + 0.4);

      gain.gain.setValueAtTime(0.15, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);

      osc.connect(gain);
      gain.connect(this.ctx.destination);

      osc.start(now);
      osc.stop(now + 0.45);
      return true;
    } catch (e) {
      console.warn('Audio alert playback error:', e);
      return false;
    }
  }
}

export const audioAlert = new AudioAlertSynthesizer();
