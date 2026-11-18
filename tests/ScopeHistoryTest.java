import fr.orbivia.accorps.ScopeHistory;
public final class ScopeHistoryTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  ScopeHistory h=new ScopeHistory(4);short[] values=new short[48*6];for(int i=0;i<values.length;i++)values[i]=(short)((i/48+1)*100);values[48*3+5]=-9999;values[48*3+6]=9998;h.append(values,values.length);
  ScopeHistory.Snapshot full=h.snapshot(4);check(full.lows.length==4&&full.lows[0]==300&&full.highs[3]==600,"Wrapped history remains chronological");check(full.lows[1]==-9999&&full.highs[1]==9998,"Peak-preserving decimation");check(full.energy.length==4&&full.energy[0]==300&&full.energy[3]==600,"RMS energy preserved for envelope");
  ScopeHistory.Snapshot fast=h.snapshot(2);check(fast.windowMillis==2&&fast.lows[0]==500&&fast.highs[1]==600,"Shorter visible window preserves latest data");check(h.snapshot(0).windowMillis==1,"Window bounded");
  h.clear();check(h.snapshot(4).lows.length==0,"New sessions clear history");short[] partial=new short[47];partial[0]=Short.MIN_VALUE;h.append(partial,47);check(h.snapshot(4).lows.length==0,"Incomplete bins retained internally");h.append(new short[]{32000},1);check(h.snapshot(4).lows[0]==Short.MIN_VALUE&&h.snapshot(4).highs[0]==32000,"Peaks survive audio block boundaries");check(h.snapshot(4).energy[0]>0,"RMS survives audio block boundaries");
  ScopeHistory timed=new ScopeHistory(20000);short[] second=new short[48000];timed.append(second,second.length);check(timed.snapshot(5000).lows.length==1000,"One bucket is one millisecond at 48 kHz");check(timed.snapshot(10000).windowMillis==10000&&timed.snapshot(2500).windowMillis==2500,"Display speed is a view window only");
  System.out.println("Scope history: all checks passed");
 }
}
