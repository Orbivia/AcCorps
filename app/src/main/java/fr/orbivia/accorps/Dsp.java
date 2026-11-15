package fr.orbivia.accorps;

/** Stateful high-pass + low-pass; deliberately no voice noise suppression. */
public final class Dsp {
 private double previous, hp, lp;
 private int lastProfile = -1;
 private float ramp;
 public void process(short[] in, short[] out, int n, int profile, float gainDb) {
  if (profile != lastProfile) { previous = hp = lp = 0; lastProfile = profile; ramp = 0; }
  double low = profile == 0 ? 25 : 80, high = profile == 0 ? 400 : 2000;
  double ha = Math.exp(-2 * Math.PI * low / 48000), la = 1 - Math.exp(-2 * Math.PI * high / 48000);
  double gain = Math.pow(10, gainDb / 20);
  for (int i = 0; i < n; i++) {
   double x = in[i] / 32768.0;
   hp = ha * (hp + x - previous); previous = x;
   lp += la * (hp - lp);
   ramp = Math.min(1, ramp + 1f / 4800);
   double y = (profile == 2 ? x : lp) * gain * ramp;
   // Bounded output, not a guarantee of safe sound pressure in arbitrary headphones.
   out[i] = (short)(Math.tanh(y) * 24000);
  }
 }
}
