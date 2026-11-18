package fr.orbivia.accorps;

/** Streaming second-order Butterworth band filtering with smoothed parameter changes. */
public final class Dsp {
 public static final double SAMPLE_RATE=48000.0;
 public static final double HEART_LOW_HZ=20, HEART_HIGH_HZ=400;
 public static final double LUNGS_LOW_HZ=100, LUNGS_HIGH_HZ=2000;
 public static final class Settings {
  public final double lowHz,highHz,gainDb;
  public final boolean enabled,notch;
  public Settings(double low,double high,double gain,boolean enabled,boolean notch) {
   lowHz=clamp(low,10,500); highHz=clamp(high,lowHz+20,4000);
   gainDb=clamp(gain,-12,24);this.enabled=enabled;this.notch=notch;
  }
  private static double clamp(double x,double min,double max){return Double.isFinite(x)?Math.max(min,Math.min(max,x)):min;}
 }
 private static final double SMOOTH=1-Math.exp(-1.0/960.0);
 private static final double Q=1.0/Math.sqrt(2.0);
 private final Biquad highPass=new Biquad(),lowPass=new Biquad();
 private double gain=1,wet=1,notchWet,ramp;
 private boolean initialized;
 private double x1,x2,y1,y2;
 private final double omega=2*Math.PI*50/SAMPLE_RATE, alpha=Math.sin(omega)/40;
 private final double nb0=1/(1+alpha),nb1=-2*Math.cos(omega)/(1+alpha),nb2=nb0,na1=nb1,na2=(1-alpha)/(1+alpha);
 private static final class Biquad {
  private double b0,b1,b2,a1,a2,tb0,tb1,tb2,ta1,ta2,z1,z2;
  private boolean ready;
  void configure(boolean high,double hz){
   double w=2*Math.PI*hz/SAMPLE_RATE,cs=Math.cos(w),al=Math.sin(w)/(2*Q);
   double norm=1/(1+al),base=high?(1+cs):(1-cs);
   tb0=base*0.5*norm;tb1=(high?-base:base)*norm;tb2=tb0;
   ta1=-2*cs*norm;ta2=(1-al)*norm;
   if(!ready){b0=tb0;b1=tb1;b2=tb2;a1=ta1;a2=ta2;ready=true;}
  }
  double next(double x){
   b0+=SMOOTH*(tb0-b0);b1+=SMOOTH*(tb1-b1);b2+=SMOOTH*(tb2-b2);
   a1+=SMOOTH*(ta1-a1);a2+=SMOOTH*(ta2-a2);
   double y=b0*x+z1;z1=b1*x-a1*y+z2;z2=b2*x-a2*y;
   return y;
  }
 }
 public void process(short[] in,short[] out,int count,int profile,float db){
  process(in,out,count,new Settings(profile==0?HEART_LOW_HZ:LUNGS_LOW_HZ,
   profile==0?HEART_HIGH_HZ:LUNGS_HIGH_HZ,db,profile!=2,false));
 }
 public void process(short[] in,short[] out,int count,Settings settings){
  if(in==null||out==null||settings==null||count<0||count>in.length||count>out.length)throw new IllegalArgumentException("Invalid audio buffer");
  highPass.configure(true,settings.lowHz);lowPass.configure(false,settings.highHz);
  double targetGain=Math.pow(10,settings.gainDb/20);
  double targetWet=settings.enabled?1:0,targetNotch=settings.enabled&&settings.notch?1:0;
  if(!initialized){gain=targetGain;wet=targetWet;notchWet=targetNotch;initialized=true;}
  for(int i=0;i<count;i++){
   gain+=SMOOTH*(targetGain-gain);wet+=SMOOTH*(targetWet-wet);
   notchWet+=SMOOTH*(targetNotch-notchWet);
   double x=in[i]/32768.0,band=lowPass.next(highPass.next(x));
   double mixed=x+wet*(band-x);
   double rejected=nb0*mixed+nb1*x1+nb2*x2-na1*y1-na2*y2;
   x2=x1;x1=mixed;y2=y1;y1=rejected;
   double filtered=mixed+notchWet*(rejected-mixed);
   ramp=Math.min(1,ramp+1.0/4800);
   out[i]=(short)(Math.tanh(filtered*gain*ramp)*24000);
  }
 }
}
