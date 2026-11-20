package fr.orbivia.accorps;

/**
 * Experimental two-grain pitch-up transposition for monitoring only.
 * Input/output sample counts match. The original WAV is saved before this stage.
 * Grain overlap introduces latency and artifacts; never treat the output as diagnostic audio.
 */
public final class PitchShifter {
 private static final int SIZE=16384, MIN_DELAY=384, SPAN=4800;
 private final float[] ring=new float[SIZE];
 private long position;
 private double phase,smoothedMix,smoothedFactor=1;
 private boolean primed;

 public void reset(){java.util.Arrays.fill(ring,0);position=0;phase=0;smoothedMix=0;smoothedFactor=1;primed=false;}
 private static double clamp(double v,double lo,double hi){return Double.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
 private double read(double delay){
  double at=position-delay;long first=(long)Math.floor(at);double f=at-first;
  float a=ring[(int)(first&(SIZE-1))],b=ring[(int)((first+1)&(SIZE-1))];
  return a+(b-a)*f;
 }
 /** factor from 1 to 5; mix from 0 (original) to 1 (transposed). */
 public void process(short[] input,short[] output,int count,double factor,double mix){
  if(input==null||output==null||count<0||count>input.length||count>output.length)throw new IllegalArgumentException("Audio buffer");
  factor=clamp(factor,1,5);mix=clamp(mix,0,1);
  if(!primed){smoothedFactor=factor;smoothedMix=0;primed=true;}
  for(int i=0;i<count;i++){
   double original=input[i]/32768.0;
   ring[(int)(position&(SIZE-1))]=(float)original;
   smoothedFactor+=.002*(factor-smoothedFactor);
   double targetMix=smoothedFactor>1.01?mix:0;
   smoothedMix+=.002*(targetMix-smoothedMix);
   double shifted=original;
   if(smoothedFactor>1.01 && position>MIN_DELAY+SPAN){
    phase+=(smoothedFactor-1)/SPAN;
    if(phase>=1)phase-=Math.floor(phase);
    double other=phase+.5;if(other>=1)other-=1;
    double w=.5-.5*Math.cos(2*Math.PI*phase);
    shifted=w*read(MIN_DELAY+SPAN*(1-phase))+(1-w)*read(MIN_DELAY+SPAN*(1-other));
   }else if(position<=MIN_DELAY+SPAN)smoothedMix=0;
   double sample=(1-smoothedMix)*original+smoothedMix*shifted;
   sample=Math.max(-.98,Math.min(.98,sample));
   output[i]=(short)Math.round(sample*32767);
   position++;
  }
 }
}
