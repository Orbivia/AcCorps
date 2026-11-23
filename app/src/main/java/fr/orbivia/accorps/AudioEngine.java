package fr.orbivia.accorps;
import android.content.*;
import android.media.*;
import android.os.Process;
import java.io.*;

public final class AudioEngine {
 public interface Listener { void block(short[] data, int n); void ended(String error); }
 private final AudioManager manager;
 private final Context context;
 private final Listener listener;
 private volatile boolean running;
 private final Object transport = new Object();
 private volatile boolean paused;
 private volatile boolean captureReady;
 private volatile boolean liveListening;
 private Thread worker;
 private Wav wav;
 private volatile long processedFrames;
 public long processedFrames(){return processedFrames;}
 public volatile Dsp.Settings settings = new Dsp.Settings(Dsp.HEART_LOW_HZ,Dsp.HEART_HIGH_HZ,0,true,false);
 public volatile AudioChannels.Mode outputMode = AudioChannels.Mode.LEFT;
 public volatile boolean rawCapture=true;
 /** Temporary listening comparison; never changes source recording or saved presets. */
 public volatile boolean compareRaw=false;
 /** Experimental monitoring-only pitch transposition (saved WAV always stays original). */
 public volatile boolean pitchEnabled=false;
 public volatile double pitchFactor=3.0;
 public volatile int pitchMix=100;
 public volatile float inputPeak=0,outputPeak=0;
 public volatile boolean limiterActive=false;
 private volatile AudioTrack output;
 public AudioEngine(Context c, Listener l) { context=c.getApplicationContext(); manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE); listener=l; }
 private boolean wired(AudioDeviceInfo d) { return d.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET || d.getType()==AudioDeviceInfo.TYPE_USB_HEADSET; }
 public AudioDeviceInfo inputDevice() { for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) if(wired(d)) return d; return null; }
 public AudioDeviceInfo outputDevice() { for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) if(wired(d) || d.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES) return d; return null; }
 public synchronized boolean isRunning() { return worker!=null; }
 public synchronized boolean isRecording() { return wav!=null; }
 public boolean isPaused(){return paused;}
 public void setPaused(boolean value) {
  synchronized(transport) {
   paused=value;
   AudioTrack t=output;
   if(t!=null) try { if(value){t.pause();t.flush();}else if(running)t.play(); } catch(IllegalStateException ignored) {}
   transport.notifyAll();
  }
 }
 public boolean isLiveListening(){return running && liveListening;}
 public synchronized void record(File f) throws IOException { if(worker==null||!running||!captureReady) throw new IOException(context.getString(R.string.err_start_record)); if(wav==null) wav=new Wav(f); }
 public synchronized void finishRecord() throws IOException { if(wav!=null) { Wav w=wav; wav=null; w.close(); } }
 public synchronized void start(File playback) {
  if(worker!=null) return;
  paused=false;processedFrames=0;inputPeak=0;outputPeak=0;limiterActive=false;compareRaw=false;captureReady=false;liveListening=playback==null;running=true; worker=new Thread(()->loop(playback),"AcCorps-audio"); worker.start();
 }
 public void stop() { synchronized(transport) { running=false; transport.notifyAll(); AudioTrack t=output; if(t!=null) try { t.pause(); t.flush(); } catch(IllegalStateException ignored) {} } }
 @android.annotation.SuppressLint("MissingPermission")
 private void loop(File playback) {
  AudioRecord mic=null; AudioTrack track=null; RandomAccessFile file=null; String error=null;
  AudioFocusRequest focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->{ if(change<0) stop(); }).build();
  try {
   if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IOException(context.getString(R.string.err_focus));
   Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
   // Live listening requires an external microphone and headphones. Playback does not.
   boolean live=playback==null;
   AudioDeviceInfo sink=live?outputDevice():null,source=live?inputDevice():null;
   if(live&&(sink==null||source==null))throw new IOException(context.getString(R.string.err_headset));
   int minOut=AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
   int minIn=live?AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT):0;
   if(minOut<0||(live&&minIn<0))throw new IOException(context.getString(R.string.err_format));
   track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
    .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
    .setBufferSizeInBytes(Math.max(minOut,3840)).setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
   if(live && !track.setPreferredDevice(sink)) throw new IOException(context.getString(R.string.err_output));
   output=track;
   if(playback==null) {
    mic=new AudioRecord.Builder().setAudioSource(rawCapture?MediaRecorder.AudioSource.UNPROCESSED:MediaRecorder.AudioSource.MIC).setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(Math.max(minIn,3840)).build();
    if(mic.getState()!=AudioRecord.STATE_INITIALIZED || !mic.setPreferredDevice(source)) throw new IOException(context.getString(R.string.err_mic));
    mic.startRecording();captureReady=true;
   } else { if(Wav.dataBytes(playback)<=0)throw new IOException(context.getString(R.string.invalid_recording));file=new RandomAccessFile(playback,"r");file.seek(44); }
   Dsp dsp=new Dsp(); PitchShifter pitch=new PitchShifter();
   short[] raw=new short[480],processed=new short[480],monitor=new short[480],stereo=new short[960];byte[] bytes=new byte[960];
   synchronized(transport) { if(running && !paused)track.play(); }
   while(running) {
    if(!live) synchronized(transport) { while(paused && running)transport.wait(); }
    if(!running)break;
    int n;
    if(file==null) { n=mic.read(raw,0,raw.length,AudioRecord.READ_BLOCKING); if(n<=0) throw new IOException(context.getString(R.string.err_mic_read)); }
    else { int count=file.read(bytes); if(count<0) break; n=count/2; for(int i=0;i<n;i++) raw[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8)); }
    if(!running)break;
    // Drain live input while paused so resuming never replays buffered microphone audio.
    if(live && paused)continue;
    AudioDeviceInfo routed=track.getRoutedDevice();
    if(live){
     if(outputDevice()==null||inputDevice()==null)throw new IOException(context.getString(R.string.err_unplugged));
     if(routed!=null&&routed.getId()!=sink.getId())throw new IOException(context.getString(R.string.err_route));
     if(mic.getRoutedDevice()!=null&&mic.getRoutedDevice().getId()!=source.getId())throw new IOException(context.getString(R.string.err_source));
    }
    synchronized(transport) {
    if(!live)while(paused && running)transport.wait();
    if(paused || !running)continue;
    synchronized(this) { if(wav!=null) wav.write(raw,n); }
    Dsp.Settings current=settings;
    if(compareRaw && current.enabled)current=new Dsp.Settings(current.lowHz,current.highHz,current.gainDb,false,current.notch,current.focused);
    dsp.process(raw,processed,n,current);
    inputPeak=(float)dsp.lastInputPeak;
    outputPeak=(float)Math.min(1,dsp.lastPreLimitPeak);
    limiterActive=dsp.lastPreLimitPeak>0.82;
    AudioChannels.Mode mode=outputMode;
    // Ensure mono phone speakers get a full-level stereo feed.
    if(!live&&routed!=null&&(routed.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
     ||routed.getType()==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))mode=AudioChannels.Mode.STEREO;
    pitch.process(processed,monitor,n,pitchEnabled&&!compareRaw?pitchFactor:1.0,
       pitchEnabled&&!compareRaw?pitchMix/100.0:0.0);
    AudioChannels.route(monitor,stereo,n,mode);
    int samples=2*n;
    int offset=0; while(offset<samples && running) { int written=track.write(stereo,offset,samples-offset,AudioTrack.WRITE_BLOCKING); if(written<=0) throw new IOException(context.getString(R.string.err_write)); offset+=written; }
    processedFrames+=n;
    listener.block(processed,n);
    }
   }
   // Allow the last queued playback block to finish before releasing the track.
   if(file!=null && running) { Thread.sleep(80); }
  } catch(Exception e) { error=e.getMessage(); }
  finally {
   if(mic!=null) { try { mic.stop(); } catch(Exception ignored) {} mic.release(); }
   synchronized(transport) { output=null; }
   manager.abandonAudioFocusRequest(focus);
   if(track!=null) { try { track.stop(); } catch(Exception ignored) {} track.release(); }
   if(file!=null) try { file.close(); } catch(IOException ignored) {}
   try { finishRecord(); } catch(IOException e) { error=context.getString(R.string.err_save,e.getMessage()); }
   inputPeak=0;outputPeak=0;limiterActive=false;compareRaw=false;
   synchronized(this) { captureReady=false;liveListening=false;running=false; worker=null; }
   listener.ended(error);
  }
 }
}
