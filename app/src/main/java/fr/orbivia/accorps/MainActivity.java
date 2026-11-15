package fr.orbivia.accorps;
import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
 private final int ink=Color.rgb(232,239,247), teal=Color.rgb(112,219,196), paper=Color.rgb(14,22,34);
 private AudioEngine engine;
 private LinearLayout page, sessions;
 private TextView state, timer, gainLabel;
 private Button listen, record;
 private SeekBar lowSlider, highSlider;
 private TextView lowLabel, highLabel;
 private Switch filterSwitch, notchSwitch;
 private Button[] presets=new Button[3];
 private boolean syncing;
 private Wave wave;
 private long recordAt;
 private File recording, export;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final AudioDeviceCallback devices=new AudioDeviceCallback() { @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { engine.stop(); } };
 private final Runnable tick=new Runnable() { public void run() { if(engine!=null && engine.isRecording()) timer.setText(String.format(Locale.FRANCE,"%02d:%02d",(SystemClock.elapsedRealtime()-recordAt)/60000,((SystemClock.elapsedRealtime()-recordAt)/1000)%60)); ui.postDelayed(this,250); } };
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  engine=new AudioEngine(this,new AudioEngine.Listener() {
   long last;
   public void block(short[] d,int n) { if(SystemClock.elapsedRealtime()-last>60) { last=SystemClock.elapsedRealtime(); short[] copy=Arrays.copyOf(d,n); ui.post(()->wave.update(copy)); } }
   public void ended(String error) { ui.post(()->{ if(isDestroyed() || isFinishing()) return; listen.setText("Écouter"); record.setText("● Enregistrer"); record.setEnabled(false); state.setText(error==null?"Prêt pour une nouvelle écoute":error); if(recording!=null) { File saved=recording; recording=null; name(saved); } }); }
  });
  getWindow().setStatusBarColor(paper); getWindow().setNavigationBarColor(paper);
  getWindow().getDecorView().setSystemUiVisibility(0);
  ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(paper);
  page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24),dp(24),dp(24),dp(20)); scroll.addView(page); setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{ v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
  text("ACCorps",30,ink); text("STÉTHOSCOPE NUMÉRIQUE",11,teal);
  space(24); state=text("Connecter un micro filaire ou USB",13,teal);
  space(18); text("Écouter. Affiner.",28,ink);
  text("Les réglages s’appliquent sans interrompre le son.",13,Color.rgb(150,167,185));
  space(12); wave=new Wave(this); wave.setBackground(round(Color.rgb(23,35,51))); page.addView(wave,new LinearLayout.LayoutParams(-1,dp(165)));
  timer=text("00:00",32,ink); timer.setGravity(Gravity.CENTER); timer.setPadding(0,dp(12),0,dp(12));
  LinearLayout transport=new LinearLayout(this); page.addView(transport);
  listen=button("Écouter",transport); listen.setTextColor(paper); listen.setBackground(round(teal));
  record=button("● Enregistrer",transport); record.setEnabled(false);
  space(26); text("Façonnez votre écoute",22,ink);
  space(10); LinearLayout profiles=new LinearLayout(this); page.addView(profiles); String[] names={"Cœur","Poumons","Brut"};
  for(int i=0;i<3;i++) { final int index=i; presets[i]=button(names[i],profiles); presets[i].setOnClickListener(v->preset(index)); }
  space(14);
  filterSwitch=new Switch(this); filterSwitch.setText("Filtres en direct"); filterSwitch.setTextColor(ink); filterSwitch.setChecked(true); filterSwitch.setMinHeight(dp(48)); page.addView(filterSwitch);
  lowLabel=text("Coupe-bas   25 Hz",15,ink); lowSlider=slider(490,15); 
  highLabel=text("Coupe-haut   400 Hz",15,ink); highSlider=slider(3900,300);
  lowSlider.setOnSeekBarChangeListener(change(()->{ if(syncing)return; int low=lowSlider.getProgress()+10; if(highSlider.getProgress()+100<low+20) highSlider.setProgress(low+20-100); applyFilters(); }));
  highSlider.setOnSeekBarChangeListener(change(()->{ if(syncing)return; int high=highSlider.getProgress()+100; if(lowSlider.getProgress()+10>high-20) lowSlider.setProgress(Math.max(0,high-30)); applyFilters(); }));
  notchSwitch=new Switch(this); notchSwitch.setText("Réjecteur secteur · 50 Hz"); notchSwitch.setTextColor(ink); notchSwitch.setMinHeight(dp(48)); page.addView(notchSwitch);
  notchSwitch.setOnCheckedChangeListener((b,on)->applyFilters());
  filterSwitch.setOnCheckedChangeListener((b,on)->{ lowSlider.setEnabled(on);highSlider.setEnabled(on);notchSwitch.setEnabled(on);applyFilters(); });
  space(8); gainLabel=text("Gain d’écoute   0 dB",15,ink);
  SeekBar gain=slider(36,12); gain.setOnSeekBarChangeListener(change(()->{ Dsp.Settings old=engine.settings; engine.settings=new Dsp.Settings(old.lowHz,old.highHz,gain.getProgress()-12,old.enabled,old.notch); gainLabel.setText("Gain d’écoute   "+(gain.getProgress()-12)+" dB"); }));
  text("Commencer avec un volume casque faible.",12,Color.rgb(150,167,185));
  preset(0);
  listen.setOnClickListener(v->{
   if(engine.isRunning()) { engine.stop(); return; }
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1); return; }
   live();
  });
  record.setOnClickListener(v->{
   try { if(engine.isRecording()) { engine.finishRecord(); record.setText("● Enregistrer"); File saved=recording; recording=null; name(saved); }
    else { File dir=new File(getFilesDir(),"sessions"); dir.mkdirs(); recording=new File(dir,"Session_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+".wav"); engine.record(recording); recordAt=SystemClock.elapsedRealtime(); record.setText("■ Terminer"); }
   } catch(IOException e) { state.setText(e.getMessage()); }
  });
  space(24); text("Mes enregistrements",22,ink); text("Son brut conservé • Réglages modifiables à la réécoute",12,teal);
  sessions=new LinearLayout(this); sessions.setOrientation(LinearLayout.VERTICAL); page.addView(sessions); library();
  space(20); text("Prototype d’écoute et d’enregistrement. Aucun diagnostic automatique.",12,ink);
  ((AudioManager)getSystemService(AUDIO_SERVICE)).registerAudioDeviceCallback(devices,ui); ui.post(tick);
 }
 private SeekBar slider(int max,int progress) { SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(progress);bar.setMinHeight(dp(44));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(teal));bar.setThumbTintList(android.content.res.ColorStateList.valueOf(teal));page.addView(bar);return bar; }
 private SeekBar.OnSeekBarChangeListener change(Runnable r) { return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){r.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}}; }
 private void applyFilters() { if(syncing)return; Dsp.Settings old=engine.settings;engine.settings=new Dsp.Settings(lowSlider.getProgress()+10,highSlider.getProgress()+100,old.gainDb,filterSwitch.isChecked(),notchSwitch.isChecked());lowLabel.setText("Coupe-bas   "+(int)engine.settings.lowHz+" Hz");highLabel.setText("Coupe-haut   "+(int)engine.settings.highHz+" Hz");for(Button b:presets)b.setBackground(round(Color.rgb(27,40,58))); }
 private void preset(int index) { syncing=true;lowSlider.setProgress(index==0?15:70);highSlider.setProgress(index==0?300:1900);filterSwitch.setChecked(index!=2);notchSwitch.setChecked(false);syncing=false;applyFilters();presets[index].setBackground(round(Color.rgb(38,76,72))); }
 private void live() { engine.start(null); listen.setText("Arrêter"); record.setEnabled(true); state.setText("Micro externe → filtre → écouteurs"); }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants) { super.onRequestPermissionsResult(r,p,grants); if(r==1 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) live(); else state.setText("Autorisation microphone nécessaire pour écouter et enregistrer"); }
 private void name(File f) {
  if(f==null || !f.exists()) return;
  if(isDestroyed() || isFinishing() || !hasWindowFocus()) { library(); return; }
  EditText input=new EditText(this); input.setSingleLine(); input.setText(f.getName().replace(".wav",""));
  new AlertDialog.Builder(this).setTitle("Nom de la session").setView(input).setPositiveButton("Enregistrer",(d,w)->{ String label=input.getText().toString().trim().replaceAll("[^\\p{L}\\p{N} _-]","_"); if(!label.isEmpty()) { File target=new File(f.getParentFile(),label+".wav"); if(!target.equals(f) && (target.exists() || !f.renameTo(target))) Toast.makeText(this,"Nom indisponible : nom d’origine conservé",Toast.LENGTH_LONG).show(); } library(); }).setNegativeButton("Garder le nom",(d,w)->library()).setOnCancelListener(d->library()).show();
 }
 private void library() {
  sessions.removeAllViews(); File[] files=new File(getFilesDir(),"sessions").listFiles((dir,n)->n.endsWith(".wav"));
  if(files==null || files.length==0) { TextView empty=new TextView(this); empty.setText("Vos prochaines écoutes apparaîtront ici."); empty.setTextColor(ink); empty.setPadding(0,dp(18),0,dp(18)); sessions.addView(empty); return; }
  Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
  for(File f:files) {
   if(recording!=null && recording.equals(f)) continue;
   LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,dp(12),0,dp(12)); sessions.addView(row);
   TextView title=new TextView(this); title.setText(f.getName().replace(".wav","")+"  ·  "+Math.max(0,(f.length()-44)/96000)+" s"); title.setTextColor(ink); title.setTextSize(16); row.addView(title);
   LinearLayout actions=new LinearLayout(this); row.addView(actions);
   button("Écouter",actions).setOnClickListener(v->{ if(engine.isRunning()) { state.setText("Arrêtez l’écoute en cours avant de relire une session"); return; } engine.start(f); listen.setText("Arrêter"); record.setEnabled(false); state.setText("Réécoute · "+f.getName()); });
   button("Exporter WAV",actions).setOnClickListener(v->{ export=f; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/wav").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,f.getName()); startActivityForResult(i,2); });
  }
 }
 @Override protected void onActivityResult(int request,int result,Intent data) {
  super.onActivityResult(request,result,data);
  if(request==2 && result==RESULT_OK && data!=null && export!=null) {
   File source=export; Uri uri=data.getData(); new Thread(()->{ try(InputStream in=new FileInputStream(source); OutputStream out=getContentResolver().openOutputStream(uri)) { if(out==null) throw new IOException(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); ui.post(()->Toast.makeText(this,"WAV exporté",Toast.LENGTH_SHORT).show()); } catch(IOException e) { ui.post(()->Toast.makeText(this,"Export impossible",Toast.LENGTH_LONG).show()); } }).start();
  }
 }
 @Override protected void onPause() { super.onPause(); engine.stop(); }
 @Override protected void onDestroy() { engine.stop(); ui.removeCallbacksAndMessages(null); ((AudioManager)getSystemService(AUDIO_SERVICE)).unregisterAudioDeviceCallback(devices); super.onDestroy(); }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private void space(int n){Space s=new Space(this);page.addView(s,new LinearLayout.LayoutParams(1,dp(n)));}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);page.addView(t);return t;}
 private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));return d;}
 private Button button(String label,LinearLayout parent){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(ink);b.setTextSize(14);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));b.setBackground(round(Color.rgb(27,40,58)));b.setBackgroundTintList(null);b.setMinHeight(dp(52));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,-2,parent.getOrientation()==LinearLayout.HORIZONTAL?1:0);p.setMargins(dp(2),dp(3),dp(2),dp(3));parent.addView(b,p);return b;}
 private class Wave extends View {
  private short[] data=new short[0]; private final Path trace=new Path(); private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  Wave(Context c){super(c);setContentDescription("Forme d’onde audio filtrée");}
  void update(short[] d){data=d;invalidate();}
  protected void onDraw(Canvas c){super.onDraw(c);paint.setColor(Color.rgb(46,63,82));paint.setStrokeWidth(dp(1));c.drawLine(0,getHeight()/2f,getWidth(),getHeight()/2f,paint);if(data.length<2)return;paint.setColor(teal);paint.setStrokeWidth(dp(2));Path p=trace;p.reset();for(int i=0;i<data.length;i++){float x=i*getWidth()/(float)(data.length-1),y=getHeight()/2f-data[i]/32768f*getHeight()*.45f;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}
 }
}
