package fr.orbivia.accorps;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Original 48 kHz mono PCM16 WAV, unprocessed by the app. */
public final class Wav implements Closeable {
 private final RandomAccessFile f;
 private long bytes;
 private byte[] buffer=new byte[960];
 private boolean closed;
 public Wav(File path)throws IOException{
  f=new RandomAccessFile(path,"rw");f.setLength(0);header(0);
 }
 public synchronized void write(short[] samples,int n)throws IOException{
  if(closed)throw new IOException("Closed recording");
  if(samples==null||n<0||n>samples.length)throw new IllegalArgumentException("Invalid samples");
  if(bytes+n*2L>0xfffffff0L-44)throw new IOException("Recording too long");
  if(buffer.length<n*2)buffer=new byte[n*2];
  for(int i=0;i<n;i++){buffer[i*2]=(byte)samples[i];buffer[i*2+1]=(byte)(samples[i]>>8);}
  f.write(buffer,0,n*2);bytes+=n*2L;
 }
 private void le(int n)throws IOException{f.write(n);f.write(n>>8);f.write(n>>16);f.write(n>>24);}
 private void header(long data)throws IOException{
  f.seek(0);f.writeBytes("RIFF");le((int)(data+36));f.writeBytes("WAVEfmt ");le(16);
  f.write(new byte[]{1,0,1,0});le(48000);le(96000);f.write(new byte[]{2,0,16,0});
  f.writeBytes("data");le((int)data);f.seek(44+data);
 }
 @Override public synchronized void close()throws IOException{
  if(closed)return;closed=true;
  try{header(bytes);f.getFD().sync();}finally{f.close();}
 }
 private static long u32(byte[] h,int p){
  return (h[p]&255L)|((h[p+1]&255L)<<8)|((h[p+2]&255L)<<16)|((h[p+3]&255L)<<24);
 }
 private static boolean match(byte[] b,int p,String expected){
  byte[] key=expected.getBytes(StandardCharsets.US_ASCII);
  for(int i=0;i<key.length;i++)if(b[p+i]!=key[i])return false;
  return true;
 }
 public static long dataBytes(File path)throws IOException{
  if(path==null||!path.isFile())throw new IOException("Missing WAV");
  try(RandomAccessFile r=new RandomAccessFile(path,"r")){
   if(r.length()<44)throw new IOException("Missing WAV header");
   byte[] b=new byte[44];r.readFully(b);
   long payload=u32(b,40);
   if(!match(b,0,"RIFF")||!match(b,8,"WAVE")||!match(b,12,"fmt ")||!match(b,36,"data")
     ||u32(b,16)!=16||b[20]!=1||b[21]!=0||b[22]!=1||b[23]!=0
     ||u32(b,24)!=48000||b[34]!=16||b[35]!=0
     ||(payload&1)!=0||u32(b,4)!=payload+36||r.length()!=payload+44)
    throw new IOException("Invalid or truncated WAV");
   return payload;
  }
 }
}
