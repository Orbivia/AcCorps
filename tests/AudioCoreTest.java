import fr.orbivia.accorps.*;
import java.io.*;
public class AudioCoreTest {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static double response(int profile,int hz){Dsp d=new Dsp();short[] in=new short[48000],out=new short[48000];for(int i=0;i<in.length;i++)in[i]=(short)(1000*Math.sin(2*Math.PI*hz*i/48000));d.process(in,out,in.length,profile,0);double sum=0;for(int i=24000;i<48000;i++)sum+=out[i]*(double)out[i];return Math.sqrt(sum/24000);}
 public static void main(String[] a)throws Exception{
  require(response(0,100)>response(0,4000)*5,"Heart rejects high frequencies");
  require(response(1,600)>response(1,10)*4,"Lungs rejects near DC");
  Dsp d=new Dsp();short[] in=new short[10000],out=new short[10000];java.util.Arrays.fill(in,Short.MAX_VALUE);d.process(in,out,in.length,2,24);for(short x:out)require(Math.abs((int)x)<=24000,"Output bound");
  File f=File.createTempFile("accorps-test",".wav");try(Wav w=new Wav(f)){w.write(new short[]{-32768,0,32767},3);}try(RandomAccessFile r=new RandomAccessFile(f,"r")){require(r.length()==50,"WAV length");require(r.readInt()==0x52494646,"RIFF");r.seek(40);require(r.readUnsignedByte()==6,"Data size");r.seek(44);require(r.readUnsignedByte()==0&&r.readUnsignedByte()==128,"PCM little endian");}f.delete();System.out.println("Audio core: all checks passed");
 }
}
