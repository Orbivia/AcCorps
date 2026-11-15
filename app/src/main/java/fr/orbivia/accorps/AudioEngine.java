package fr.orbivia.accorps;
import android.content.*;
import android.media.*;
import android.os.Process;
import java.io.*;

public final class AudioEngine {
 public interface Listener { void block(short[] data, int n); void ended(String error); }
 private final AudioManager manager;
 private final Listener listener;
 private volatile boolean running;
 private Thread worker;
 private Wav wav;
 public volatile Dsp.Settings settings = new Dsp.Settings(25,400,0,true,false);
 private volatile AudioTrack output;
 public AudioEngine(Context c, Listener l) { manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE); listener=l; }
 private boolean wired(AudioDeviceInfo d) { return d.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET || d.getType()==AudioDeviceInfo.TYPE_USB_HEADSET; }
 public AudioDeviceInfo inputDevice() { for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) if(wired(d)) return d; return null; }
 public AudioDeviceInfo outputDevice() { for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) if(wired(d) || d.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES) return d; return null; }
 public synchronized boolean isRunning() { return worker!=null; }
 public synchronized boolean isRecording() { return wav!=null; }
 public synchronized void record(File f) throws IOException { if(worker==null) throw new IOException("Démarrer l’écoute avant d’enregistrer"); if(wav==null) wav=new Wav(f); }
 public synchronized void finishRecord() throws IOException { if(wav!=null) { Wav w=wav; wav=null; w.close(); } }
 public synchronized void start(File playback) {
  if(worker!=null) return;
  running=true; worker=new Thread(()->loop(playback),"AcCorps-audio"); worker.start();
 }
 public void stop() { running=false; AudioTrack t=output; if(t!=null) try { t.pause(); t.flush(); } catch(IllegalStateException ignored) {} }
 @android.annotation.SuppressLint("MissingPermission")
 private void loop(File playback) {
  AudioRecord mic=null; AudioTrack track=null; RandomAccessFile file=null; String error=null;
  AudioFocusRequest focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->{ if(change<0) stop(); }).build();
  try {
   if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IOException("Sortie audio occupée");
   Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
   AudioDeviceInfo sink=outputDevice(), source=inputDevice();
   if(sink==null || (playback==null && source==null)) throw new IOException("Brancher des écouteurs filaires avec microphone ou un casque USB compatible");
   int minOut=AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   int minIn=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(minOut<0 || minIn<0) throw new IOException("Format audio 48 kHz indisponible");
   track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
    .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setBufferSizeInBytes(Math.max(minOut,1920)).setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
   if(!track.setPreferredDevice(sink)) throw new IOException("Sortie casque indisponible");
   output=track;
   if(playback==null) {
    mic=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.UNPROCESSED).setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(minIn,3840)).build();
    if(mic.getState()!=AudioRecord.STATE_INITIALIZED || !mic.setPreferredDevice(source)) throw new IOException("Microphone externe indisponible");
    mic.startRecording();
   } else { file=new RandomAccessFile(playback,"r"); file.seek(44); }
   Dsp dsp=new Dsp(); short[] raw=new short[480], processed=new short[480]; byte[] bytes=new byte[960];
   track.play();
   while(running) {
    int n;
    if(file==null) { n=mic.read(raw,0,raw.length,AudioRecord.READ_BLOCKING); if(n<=0) throw new IOException("Lecture microphone interrompue"); }
    else { int count=file.read(bytes); if(count<0) break; n=count/2; for(int i=0;i<n;i++) raw[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8)); }
    if(outputDevice()==null || (file==null && inputDevice()==null)) throw new IOException("Écouteurs débranchés : écoute arrêtée");
    AudioDeviceInfo routed=track.getRoutedDevice();
    if(routed!=null && routed.getId()!=sink.getId()) throw new IOException("La sortie audio a changé : écoute arrêtée");
    if(mic!=null && mic.getRoutedDevice()!=null && mic.getRoutedDevice().getId()!=source.getId()) throw new IOException("La source microphone a changé");
    synchronized(this) { if(wav!=null) wav.write(raw,n); }
    dsp.process(raw,processed,n,settings);
    int offset=0; while(offset<n && running) { int written=track.write(processed,offset,n-offset,AudioTrack.WRITE_BLOCKING); if(written<0) throw new IOException("Sortie audio interrompue"); offset+=written; }
    listener.block(processed,n);
   }
   // Allow the last queued playback block to finish before releasing the track.
   if(file!=null && running) { Thread.sleep(80); }
  } catch(Exception e) { error=e.getMessage(); }
  finally {
   if(mic!=null) { try { mic.stop(); } catch(Exception ignored) {} mic.release(); }
   output=null;
   manager.abandonAudioFocusRequest(focus);
   if(track!=null) { try { track.stop(); } catch(Exception ignored) {} track.release(); }
   if(file!=null) try { file.close(); } catch(IOException ignored) {}
   try { finishRecord(); } catch(IOException e) { error="Sauvegarde incomplète : "+e.getMessage(); }
   synchronized(this) { running=false; worker=null; }
   listener.ended(error);
  }
 }
}
