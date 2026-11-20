import fr.orbivia.accorps.PitchShifter;

/** Pure Java smoke tests for low-frequency pitch-up monitoring. */
public final class PitchShifterTest {
 private static void check(int hz,int factor) {
  final int rate=48000, block=480;
  PitchShifter shifter=new PitchShifter();
  short[] input=new short[block],output=new short[block];double[] samples=new double[rate*2];
  for(int start=0;start<samples.length;start+=block){
   for(int i=0;i<block;i++)input[i]=(short)Math.round(9000*Math.sin(2*Math.PI*hz*(start+i)/(double)rate));
   shifter.process(input,output,block,factor,1);
   for(int i=0;i<block;i++)samples[start+i]=output[i]/32768.0;
  }
  int peakHz=0;double peak=0;
  for(int f=30;f<600;f+=2){
   double re=0,im=0;
   for(int i=rate;i<rate*2;i+=4){
    double angle=2*Math.PI*f*i/rate;re+=samples[i]*Math.cos(angle);im+=samples[i]*Math.sin(angle);
   }
   double power=re*re+im*im;if(power>peak){peak=power;peakHz=f;}
  }
  if(Math.abs(peakHz-hz*factor)>4)throw new AssertionError("Expected "+(hz*factor)+" Hz, got "+peakHz+" Hz");
  System.out.println(hz+" Hz x "+factor+" -> "+peakHz+" Hz");
 }
 public static void main(String[] args) {
  check(40,2);check(60,3);check(100,3);
  PitchShifter shift=new PitchShifter();short[] in={-600,0,300,1200},out=new short[4];
  shift.process(in,out,4,1,0);
  for(int i=0;i<in.length;i++)if(Math.abs(out[i]-in[i])>1)throw new AssertionError("Bypass corrupted sample");
 }
}
