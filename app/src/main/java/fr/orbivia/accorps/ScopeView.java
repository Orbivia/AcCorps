package fr.orbivia.accorps;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** Scrolling waveform or relative-amplitude (RMS) envelope, never an ECG. */
public final class ScopeView extends View {
 private final ScopeHistory history;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final int ink,grid,accent;
 private final float density;
 private int windowMillis=5000;
 private boolean envelopeMode=true;
 private ScopeHistory.Snapshot frozen;
 public ScopeView(Context c,ScopeHistory history,int ink,int grid,int accent){
  super(c);this.history=history;this.ink=ink;this.grid=grid;this.accent=accent;
  density=getResources().getDisplayMetrics().density;
  setContentDescription(c.getString(R.string.wave_description));
 }
 public void setSpeed(double speed){windowMillis=(int)(5000/speed);if(frozen!=null)frozen=history.snapshot(windowMillis);invalidate();}
 public void setEnvelopeMode(boolean mode){envelopeMode=mode;invalidate();}
 public void setPaused(boolean paused){frozen=paused?history.snapshot(windowMillis):null;invalidate();}
 public boolean isPaused(){return frozen!=null;}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);
  float left=10*density,right=getWidth()-10*density,top=8*density,bottom=getHeight()-26*density,mid=(top+bottom)/2;
  if(right<=left||bottom<=top)return;
  paint.setColor(grid);paint.setStrokeWidth(Math.max(1,density*.5f));
  for(int i=0;i<=20;i++){float x=left+(right-left)*i/20;c.drawLine(x,top,x,bottom,paint);}
  for(int i=0;i<=8;i++){float y=top+(bottom-top)*i/8;c.drawLine(left,y,right,y,paint);}
  ScopeHistory.Snapshot data=frozen==null?history.snapshot(windowMillis):frozen;
  int n=data.lows.length,padding=data.windowMillis-n,pixels=Math.max(1,(int)(right-left));
  if(envelopeMode)drawEnvelope(c,data,n,padding,pixels,left,mid,bottom-top);
  else drawWaveform(c,data,n,padding,pixels,left,mid,bottom-top);
  paint.setAlpha(255);paint.setColor(accent);paint.setStrokeWidth(2*density);
  c.drawLine(right-1,top,right-1,bottom,paint);
  paint.setTextSize(11*density);paint.setColor(ink);
  for(int i=0;i<=5;i++){
   float x=left+(right-left)*i/5;
   String label=String.format(Locale.ROOT,"%.1f s",-data.windowMillis/1000.0*(1-i/5.0));
   if(i==5)label="0 s";
   paint.setTextAlign(i==0?Paint.Align.LEFT:i==5?Paint.Align.RIGHT:Paint.Align.CENTER);
   c.drawText(label,x,getHeight()-6*density,paint);
  }
  paint.setTextAlign(Paint.Align.LEFT);
 }
 private void drawEnvelope(Canvas c,ScopeHistory.Snapshot data,int n,int padding,int pixels,float left,float mid,float height){
  // 12 ms attack / 50 ms release suppress oscillation while preserving S1/S2 bursts.
  float[] envelope=new float[n];float level=0,max=12;
  final float attack=.08f,release=.02f;
  for(int i=0;i<n;i++){
   float target=data.energy[i];level+=(target>level?attack:release)*(target-level);
   envelope[i]=level;max=Math.max(max,level);
  }
  float scale=height*.40f/max;
  paint.setColor(ink);paint.setStrokeWidth(Math.max(1,density));
  for(int p=0;p<pixels;p++){
   int from=(int)((long)p*data.windowMillis/pixels)-padding;
   int to=(int)((long)(p+1)*data.windowMillis/pixels)-padding;
   if(to<0||from>=n)continue;
   from=Math.max(0,from);to=Math.min(n,Math.max(from+1,to));if(from>=to)continue;
   float peak=0;for(int i=from;i<to;i++)peak=Math.max(peak,envelope[i]);
   float x=left+p,extent=peak*scale;
   paint.setAlpha(120+135*p/pixels);c.drawLine(x,mid-extent,x,mid+extent,paint);
  }
 }
 private void drawWaveform(Canvas c,ScopeHistory.Snapshot data,int n,int padding,int pixels,float left,float mid,float height){
  float peak=32;
  for(int i=0;i<n;i++)peak=Math.max(peak,Math.max(Math.abs((int)data.lows[i]),Math.abs((int)data.highs[i])));
  float scale=height*.43f/peak,lastY=mid;boolean connected=false;
  paint.setStrokeWidth(Math.max(1,density));
  for(int p=0;p<pixels;p++){
   int from=(int)((long)p*data.windowMillis/pixels)-padding,to=(int)((long)(p+1)*data.windowMillis/pixels)-padding;
   if(to<0||from>=n)continue;
   from=Math.max(0,from);to=Math.min(n,Math.max(from+1,to));if(from>=to)continue;
   int lo=32767,hi=-32768;
   for(int i=from;i<to;i++){lo=Math.min(lo,data.lows[i]);hi=Math.max(hi,data.highs[i]);}
   float x=left+p,upper=mid-hi*scale,lower=mid-lo*scale,y=(upper+lower)/2;
   paint.setColor(ink);paint.setAlpha(110+145*p/pixels);
   c.drawLine(x,upper,x,lower,paint);if(connected)c.drawLine(x-1,lastY,x,y,paint);
   lastY=y;connected=true;
  }
 }
}
