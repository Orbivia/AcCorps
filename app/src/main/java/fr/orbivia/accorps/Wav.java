package fr.orbivia.accorps;
import java.io.*;
public final class Wav implements Closeable {
 private final RandomAccessFile f;
 private long bytes;
 public Wav(File path) throws IOException { f = new RandomAccessFile(path, "rw"); f.setLength(0); f.write(new byte[44]); }
 public synchronized void write(short[] samples, int n) throws IOException {
  if(bytes + n * 2L > 0xfffffff0L - 44) throw new IOException("Enregistrement trop long");
  byte[] b = new byte[n * 2];
  for (int i=0; i<n; i++) { b[2*i]=(byte)samples[i]; b[2*i+1]=(byte)(samples[i]>>8); }
  f.write(b); bytes += b.length;
 }
 private void le(int n) throws IOException { f.write(n); f.write(n>>8); f.write(n>>16); f.write(n>>24); }
 public synchronized void close() throws IOException {
  f.seek(0); f.writeBytes("RIFF"); le((int)(bytes+36)); f.writeBytes("WAVEfmt "); le(16);
  f.write(new byte[]{1,0,1,0}); le(48000); le(96000); f.write(new byte[]{2,0,16,0}); f.writeBytes("data"); le((int)bytes); f.close();
 }
}
