package fr.orbivia.accorps;

/** One min/max bucket per millisecond at 48 kHz; no audio samples are skipped. */
public final class ScopeHistory {
 public static final int SAMPLES_PER_BUCKET=48;
 private final short[] lows,highs;
 private int head,size,pending,min=32767,max=-32768;
 public static final class Snapshot {
  public final short[] lows,highs;
  public final int windowMillis;
  Snapshot(short[] lows,short[] highs,int windowMillis){this.lows=lows;this.highs=highs;this.windowMillis=windowMillis;}
 }
 public ScopeHistory(int capacityMillis){if(capacityMillis<1)throw new IllegalArgumentException();lows=new short[capacityMillis];highs=new short[capacityMillis];}
 public synchronized void clear(){head=size=pending=0;min=32767;max=-32768;}
 public synchronized void append(short[] samples,int count){
  for(int i=0;i<count;i++){int v=samples[i];min=Math.min(min,v);max=Math.max(max,v);if(++pending==SAMPLES_PER_BUCKET){lows[head]=(short)min;highs[head]=(short)max;head=(head+1)%lows.length;size=Math.min(size+1,lows.length);pending=0;min=32767;max=-32768;}}
 }
 public synchronized Snapshot snapshot(int requestedMillis){int window=Math.max(1,Math.min(requestedMillis,lows.length)),n=Math.min(size,window);short[] lo=new short[n],hi=new short[n];int start=(head-n+lows.length)%lows.length;for(int i=0;i<n;i++){int j=(start+i)%lows.length;lo[i]=lows[j];hi[i]=highs[j];}return new Snapshot(lo,hi,window);}
}
