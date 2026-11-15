package fr.orbivia.accorps;

/** Streaming filters. Parameter targets change atomically at audio block boundaries. */
public final class Dsp {
 public static final class Settings {
  public final double lowHz, highHz, gainDb;
  public final boolean enabled, notch;
  public Settings(double low, double high, double gain, boolean enabled, boolean notch) {
   lowHz=finiteClamp(low,10,500); highHz=finiteClamp(high,lowHz+20,4000);
   gainDb=finiteClamp(gain,-12,24); this.enabled=enabled; this.notch=notch;
  }
  private static double finiteClamp(double x,double min,double max) { return Double.isFinite(x)?Math.max(min,Math.min(max,x)):min; }
 }
 private double previous,hp,lp,ramp,ha,la,gain,wet,notchWet;
 private double x1,x2,y1,y2;
 private boolean initialized;
 private final double omega=2*Math.PI*50/48000, alpha=Math.sin(omega)/40;
 private final double b0=1/(1+alpha), b1=-2*Math.cos(omega)/(1+alpha), b2=b0, a1=b1, a2=(1-alpha)/(1+alpha);
 public void process(short[] in,short[] out,int n,int profile,float db) {
  process(in,out,n,new Settings(profile==0?25:80,profile==0?400:2000,db,profile!=2,false));
 }
 public void process(short[] in,short[] out,int n,Settings s) {
  double targetHa=Math.exp(-2*Math.PI*s.lowHz/48000), targetLa=1-Math.exp(-2*Math.PI*s.highHz/48000), targetGain=Math.pow(10,s.gainDb/20);
  if(!initialized) { ha=targetHa; la=targetLa; gain=targetGain; wet=s.enabled?1:0; notchWet=s.enabled&&s.notch?1:0; initialized=true; }
  // 20 ms smoothing, no state reset on changes, to avoid abrupt block-boundary jumps.
  final double smooth=1-Math.exp(-1.0/960);
  for(int i=0;i<n;i++) {
   ha+=smooth*(targetHa-ha); la+=smooth*(targetLa-la); gain+=smooth*(targetGain-gain);
   wet+=smooth*((s.enabled?1:0)-wet); notchWet+=smooth*((s.enabled&&s.notch?1:0)-notchWet);
   double x=in[i]/32768.0; hp=ha*(hp+x-previous); previous=x; lp+=la*(hp-lp);
   double band=x+wet*(lp-x);
   double rejected=b0*band+b1*x1+b2*x2-a1*y1-a2*y2; x2=x1;x1=band;y2=y1;y1=rejected;
   double filtered=band+notchWet*(rejected-band);
   ramp=Math.min(1,ramp+1.0/4800);
   out[i]=(short)(Math.tanh(filtered*gain*ramp)*24000);
  }
 }
}
