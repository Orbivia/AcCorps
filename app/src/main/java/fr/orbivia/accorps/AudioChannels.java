package fr.orbivia.accorps;

/** Maps mono input onto interleaved stereo PCM. */
public final class AudioChannels {
 public enum Mode { LEFT, RIGHT, STEREO }
 private AudioChannels() {}
 public static void route(short[] mono, short[] stereo, int frames, Mode mode) {
  if(mono==null || stereo==null || mode==null || frames<0 || frames>mono.length || frames>stereo.length/2)
   throw new IllegalArgumentException("Invalid audio channel buffer or mode");
  for(int i=0;i<frames;i++) {
   short sample=mono[i];
   stereo[2*i]=mode==Mode.RIGHT?0:sample;
   stereo[2*i+1]=mode==Mode.LEFT?0:sample;
  }
 }
}
