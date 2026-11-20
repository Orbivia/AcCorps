import fr.orbivia.accorps.*;
import java.io.*;
public class AudioCoreTest {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static double response(int profile,int hz){Dsp d=new Dsp();short[] in=new short[48000],out=new short[48000];for(int i=0;i<in.length;i++)in[i]=(short)(1000*Math.sin(2*Math.PI*hz*i/48000));d.process(in,out,in.length,profile,0);double sum=0;for(int i=24000;i<48000;i++)sum+=out[i]*(double)out[i];return Math.sqrt(sum/24000);}
 static double filteredResponse(Dsp.Settings settings,int hz){Dsp d=new Dsp();short[] in=new short[240000],out=new short[240000];for(int i=0;i<in.length;i++)in[i]=(short)(1000*Math.sin(2*Math.PI*hz*i/48000));d.process(in,out,in.length,settings);double sum=0;for(int i=192000;i<240000;i++)sum+=out[i]*(double)out[i];return Math.sqrt(sum/48000);}
 public static void main(String[] a)throws Exception{
  require(filteredResponse(new Dsp.Settings(20,200,0,true,false),2000)<filteredResponse(new Dsp.Settings(20,4000,0,true,false),2000)/4,"Live cutoff changes response");
  require(filteredResponse(new Dsp.Settings(20,400,0,true,false),100)>filteredResponse(new Dsp.Settings(20,400,0,true,false),4000)*8,"Heart high-frequency rejection");
  require(filteredResponse(new Dsp.Settings(100,2000,0,true,false,true),600)>filteredResponse(new Dsp.Settings(100,2000,0,true,false,true),20)*8,"Lung low-frequency rejection");
  require(filteredResponse(new Dsp.Settings(100,2000,0,true,false,true),600)>filteredResponse(new Dsp.Settings(100,2000,0,true,false,true),7000)*5,"Lung high-frequency rejection");
  require(filteredResponse(new Dsp.Settings(10,4000,0,true,true),50)<filteredResponse(new Dsp.Settings(10,4000,0,true,false),50)/10,"50 Hz notch rejects mains");
  Dsp smooth=new Dsp();short[] dc=new short[10000],before=new short[10000],after=new short[10000];java.util.Arrays.fill(dc,(short)1000);smooth.process(dc,before,dc.length,new Dsp.Settings(25,400,0,false,false));smooth.process(dc,after,dc.length,new Dsp.Settings(300,3000,24,false,true));require(Math.abs(after[0]-before[9999])<100,"Gain change smoothed across blocks");
  Dsp.Settings invalid=new Dsp.Settings(500,20,Double.NaN,true,false);require(invalid.highHz>invalid.lowHz && Double.isFinite(invalid.gainDb),"Parameter validation");
  require(response(0,100)>response(0,4000)*5,"Heart rejects high frequencies");
  require(response(1,600)>response(1,10)*4,"Lungs rejects near DC");
  Dsp d=new Dsp();short[] in=new short[10000],out=new short[10000];java.util.Arrays.fill(in,Short.MAX_VALUE);d.process(in,out,in.length,2,24);for(short x:out)require(Math.abs((int)x)<=32120,"Output bound");
  Dsp clean=new Dsp();short[] reference=new short[24000],unity=new short[24000];
  for(int i=0;i<reference.length;i++)reference[i]=(short)(12000*Math.sin(2*Math.PI*240*i/48000));
  clean.process(reference,unity,reference.length,new Dsp.Settings(20,600,0,false,false));
  int worst=0;for(int i=6000;i<reference.length;i++)worst=Math.max(worst,Math.abs(unity[i]-reference[i]));
  require(worst<=2,"Raw zero-gain output must preserve the original PCM");
  Dsp boost=new Dsp();short[] quiet=new short[24000],loud=new short[24000];
  for(int i=0;i<quiet.length;i++)quiet[i]=(short)(100*Math.sin(2*Math.PI*180*i/48000));
  boost.process(quiet,loud,loud.length,new Dsp.Settings(20,600,36,false,false));
  require(Math.abs(loud[22050])>1000,"Manual gain to +36 dB boosts a weak signal");
  short[] original={100,-200,32767};short[] stereo=new short[6];
  AudioChannels.route(original,stereo,3,AudioChannels.Mode.LEFT);
  require(java.util.Arrays.equals(stereo,new short[]{100,0,-200,0,32767,0}),"Left output only");
  AudioChannels.route(original,stereo,3,AudioChannels.Mode.RIGHT);
  require(java.util.Arrays.equals(stereo,new short[]{0,100,0,-200,0,32767}),"Right output only");
  AudioChannels.route(original,stereo,3,AudioChannels.Mode.STEREO);
  require(java.util.Arrays.equals(stereo,new short[]{100,100,-200,-200,32767,32767}),"Stereo both channels");
  require(java.util.Arrays.equals(original,new short[]{100,-200,32767}),"Source mono unchanged");
  File f=File.createTempFile("accorps-test",".wav");try(Wav w=new Wav(f)){w.write(new short[]{-32768,0,32767},3);}try(RandomAccessFile r=new RandomAccessFile(f,"r")){require(r.length()==50,"WAV length");require(r.readInt()==0x52494646,"RIFF");r.seek(40);require(r.readUnsignedByte()==6,"Data size");r.seek(44);require(r.readUnsignedByte()==0&&r.readUnsignedByte()==128,"PCM little endian");}require(Wav.dataBytes(f)==6,"Recorded WAV data length");
  f.delete();
  File empty=File.createTempFile("accorps-empty",".wav");
  try(Wav zero=new Wav(empty)){}
  require(empty.length()==44&&Wav.dataBytes(empty)==0,"Empty WAV is detectable");
  try(Wav full=new Wav(empty)){full.write(new short[]{1,2,3,4},4);}
  require(Wav.dataBytes(empty)==8,"Non-empty recording validated");
  try(RandomAccessFile truncate=new RandomAccessFile(empty,"rw")){truncate.setLength(45);}
  boolean rejected=false;try{Wav.dataBytes(empty);}catch(IOException expected){rejected=true;}
  require(rejected,"Broken WAV must be rejected before export");
  empty.delete();
  System.out.println("Audio core: all checks passed");
 }
}
