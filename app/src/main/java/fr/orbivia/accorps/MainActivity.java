package fr.orbivia.accorps;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
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
 private static final int YELLOW=0xFFFFCC32, INK=0xFF25323C;
 private int bg,fg,muted,surface,line,accent,tab;
 private AudioEngine engine;
 private LinearLayout body,listenPanel,recordingsPanel,helpPanel,sessions;
 private TextView state,timer,gainLabel,lowLabel,highLabel;
 private Button listen,record;
 private final TextView[] nav=new TextView[3];
 private final Button[] presets=new Button[3];
 private SeekBar lowSlider,highSlider;
 private Switch filterSwitch,notchSwitch;
 private boolean syncing;
 private Wave wave;
 private long recordAt;
 private File recording,export;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final AudioDeviceCallback devices=new AudioDeviceCallback(){@Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){engine.stop();}};
 private final Runnable tick=new Runnable(){public void run(){if(engine!=null&&engine.isRecording())timer.setText(String.format(Locale.ROOT,"%02d:%02d",(SystemClock.elapsedRealtime()-recordAt)/60000,((SystemClock.elapsedRealtime()-recordAt)/1000)%60));ui.postDelayed(this,250);}};
 private String s(int id,Object... args){return getString(id,args);}
 @Override public void onCreate(Bundle savedState){
  super.onCreate(savedState);
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  bg=dark?0xFF111820:0xFFF7F8FA;fg=dark?0xFFF2F4F6:INK;muted=dark?0xFFBAC5CE:0xFF586873;surface=dark?0xFF1C2732:Color.WHITE;line=dark?0xFF344452:0xFFE2E7EB;accent=dark?0xFFF4C928:0xFFAD8100;
  engine=new AudioEngine(this,new AudioEngine.Listener(){
   long last;
   public void block(short[] d,int n){if(SystemClock.elapsedRealtime()-last>60){last=SystemClock.elapsedRealtime();short[] copy=Arrays.copyOf(d,n);ui.post(()->{if(!isDestroyed())wave.update(copy);});}}
   public void ended(String error){ui.post(()->{if(isDestroyed()||isFinishing())return;listen.setText(R.string.listen);record.setText(R.string.record);record.setEnabled(false);record.setAlpha(.45f);state.setText(error==null?s(R.string.ready):error);if(recording!=null){File saved=recording;recording=null;name(saved);}});}
  });
  LinearLayout root=column();root.setBackgroundColor(bg);
  root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(8),dp(16),dp(8));
  TextView brand=text(s(R.string.app_name),25,fg);brand.setTypeface(null,Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  Button info=new Button(this);info.setText("i");info.setAllCaps(false);info.setTextSize(20);info.setTextColor(INK);info.setBackground(shape(YELLOW));info.setBackgroundTintList(null);info.setMinimumWidth(0);info.setMinWidth(0);info.setPadding(0,0,0,0);info.setContentDescription(s(R.string.terms_info));info.setOnClickListener(v->terms());header.addView(info,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(16),dp(4),dp(16),dp(8));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  listenPanel=column();recordingsPanel=column();helpPanel=column();body.addView(listenPanel);body.addView(recordingsPanel);body.addView(helpPanel);
  buildListen();buildRecordings();buildHelp();
  LinearLayout navigation=new LinearLayout(this);navigation.setBackgroundColor(surface);int[] labels={R.string.listen,R.string.recordings,R.string.help};
  for(int i=0;i<nav.length;i++){final int index=i;nav[i]=text(s(labels[i]),13,muted);nav[i].setGravity(Gravity.CENTER);nav[i].setMinHeight(dp(56));nav[i].setFocusable(true);nav[i].setOnClickListener(v->selectTab(index));navigation.addView(nav[i],new LinearLayout.LayoutParams(0,-2,1));}
  root.addView(navigation);setContentView(root);root.requestApplyInsets();selectTab(savedState==null?0:Math.min(2,savedState.getInt("tab")));
  ((AudioManager)getSystemService(AUDIO_SERVICE)).registerAudioDeviceCallback(devices,ui);ui.post(tick);
 }
 private void buildListen(){
  listenPanel.addView(text(s(R.string.live_title),28,fg));
  LinearLayout audio=card(listenPanel);state=text(s(R.string.connect),14,muted);audio.addView(state);
  wave=new Wave(this);audio.addView(wave,new LinearLayout.LayoutParams(-1,dp(150)));
  timer=text("00:00",30,fg);timer.setGravity(Gravity.CENTER);audio.addView(timer);
  audio.addView(text(s(R.string.low_volume),13,muted));
  LinearLayout transport=new LinearLayout(this);audio.addView(transport);
  listen=button(s(R.string.listen),transport,true);record=button(s(R.string.record),transport,false);record.setEnabled(false);record.setAlpha(.45f);
  listen.setOnClickListener(v->{if(engine.isRunning()){engine.stop();return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}live();});
  record.setOnClickListener(v->{try{if(engine.isRecording()){engine.finishRecord();record.setText(R.string.record);File saved=recording;recording=null;name(saved);}else{File dir=new File(getFilesDir(),"sessions");if(!dir.exists()&&!dir.mkdirs())throw new IOException(s(R.string.err_save,"storage unavailable"));File target=new File(dir,"Session_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+".wav");engine.record(target);recording=target;recordAt=SystemClock.elapsedRealtime();timer.setText("00:00");record.setText(R.string.finish);}}catch(IOException e){state.setText(e.getMessage());}});
  LinearLayout filters=card(listenPanel);filters.addView(text(s(R.string.filters_title),20,fg));filters.addView(text(s(R.string.live_hint),13,muted));
  LinearLayout choices=new LinearLayout(this);filters.addView(choices);int[] names={R.string.heart,R.string.lungs,R.string.raw};
  for(int i=0;i<3;i++){final int index=i;presets[i]=button(s(names[i]),choices,false);presets[i].setOnClickListener(v->preset(index));}
  filterSwitch=new Switch(this);filterSwitch.setText(R.string.filters_enabled);filterSwitch.setTextColor(fg);filterSwitch.setMinHeight(dp(48));filterSwitch.setChecked(true);filters.addView(filterSwitch);
  lowLabel=text(s(R.string.high_pass,25),15,fg);filters.addView(lowLabel);lowSlider=slider(filters,490,15);
  highLabel=text(s(R.string.low_pass,400),15,fg);filters.addView(highLabel);highSlider=slider(filters,3900,300);
  lowSlider.setOnSeekBarChangeListener(change(()->{if(syncing)return;int low=lowSlider.getProgress()+10;if(highSlider.getProgress()+100<low+20)highSlider.setProgress(low+20-100);applyFilters();}));
  highSlider.setOnSeekBarChangeListener(change(()->{if(syncing)return;int high=highSlider.getProgress()+100;if(lowSlider.getProgress()+10>high-20)lowSlider.setProgress(Math.max(0,high-30));applyFilters();}));
  notchSwitch=new Switch(this);notchSwitch.setText(R.string.notch);notchSwitch.setTextColor(fg);notchSwitch.setMinHeight(dp(48));filters.addView(notchSwitch);notchSwitch.setOnCheckedChangeListener((v,on)->applyFilters());
  filterSwitch.setOnCheckedChangeListener((v,on)->{lowSlider.setEnabled(on);highSlider.setEnabled(on);notchSwitch.setEnabled(on);applyFilters();});
  gainLabel=text(s(R.string.gain,0),15,fg);filters.addView(gainLabel);SeekBar gain=slider(filters,36,12);gain.setOnSeekBarChangeListener(change(()->{Dsp.Settings old=engine.settings;engine.settings=new Dsp.Settings(old.lowHz,old.highHz,gain.getProgress()-12,old.enabled,old.notch);gainLabel.setText(s(R.string.gain,gain.getProgress()-12));}));preset(0);
 }
 private void buildRecordings(){recordingsPanel.addView(text(s(R.string.recordings),28,fg));recordingsPanel.addView(text(s(R.string.raw_saved),14,muted));sessions=column();recordingsPanel.addView(sessions);library();}
 private void buildHelp(){
  helpPanel.addView(text(s(R.string.help),28,fg));LinearLayout guide=card(helpPanel);guide.addView(text(s(R.string.how_title),20,fg));guide.addView(text(s(R.string.how_body),16,fg));
  LinearLayout usage=card(helpPanel);usage.addView(text(s(R.string.terms_title),20,fg));usage.addView(text(s(R.string.terms_hint),14,muted));button(s(R.string.terms_title),usage,false).setOnClickListener(v->terms());
  LinearLayout about=card(helpPanel);about.addView(text(s(R.string.about),20,fg));about.addView(text(s(R.string.about_body),15,muted));
 }
 private void terms(){TextView content=text(s(R.string.terms_body),16,fg);content.setTextIsSelectable(true);content.setPadding(dp(22),dp(12),dp(22),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle(R.string.terms_title).setView(scroll).setPositiveButton(R.string.close,null).show();}
 private void selectTab(int index){tab=index;listenPanel.setVisibility(index==0?View.VISIBLE:View.GONE);recordingsPanel.setVisibility(index==1?View.VISIBLE:View.GONE);helpPanel.setVisibility(index==2?View.VISIBLE:View.GONE);if(index==1)library();for(int i=0;i<nav.length;i++){nav[i].setTextColor(i==index?INK:muted);nav[i].setBackground(i==index?shape(YELLOW):shape(surface));nav[i].setTypeface(null,i==index?Typeface.BOLD:Typeface.NORMAL);nav[i].setSelected(i==index);}}
 private SeekBar slider(LinearLayout parent,int max,int value){SeekBar bar=new SeekBar(this);bar.setMax(max);bar.setProgress(value);bar.setMinimumHeight(dp(44));bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setThumbTintList(ColorStateList.valueOf(accent));parent.addView(bar);return bar;}
 private SeekBar.OnSeekBarChangeListener change(Runnable action){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int value,boolean user){action.run();}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}};}
 private void applyFilters(){if(syncing)return;Dsp.Settings old=engine.settings;engine.settings=new Dsp.Settings(lowSlider.getProgress()+10,highSlider.getProgress()+100,old.gainDb,filterSwitch.isChecked(),notchSwitch.isChecked());lowLabel.setText(s(R.string.high_pass,(int)engine.settings.lowHz));highLabel.setText(s(R.string.low_pass,(int)engine.settings.highHz));for(Button b:presets){b.setBackground(shape(line));b.setTextColor(fg);b.setSelected(false);}}
 private void preset(int index){syncing=true;lowSlider.setProgress(index==0?15:70);highSlider.setProgress(index==0?300:1900);filterSwitch.setChecked(index!=2);notchSwitch.setChecked(false);syncing=false;applyFilters();presets[index].setBackground(shape(YELLOW));presets[index].setTextColor(INK);presets[index].setSelected(true);}
 private void live(){engine.start(null);listen.setText(R.string.stop);record.setEnabled(true);record.setAlpha(1f);state.setText(R.string.live_route);}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==1&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)live();else state.setText(R.string.mic_permission);}
 private void name(File file){
  if(file==null||!file.exists())return;if(isDestroyed()||isFinishing()||!hasWindowFocus()){library();return;}
  EditText input=new EditText(this);input.setSingleLine();input.setText(file.getName().replace(".wav",""));
  new AlertDialog.Builder(this).setTitle(R.string.session_name).setView(input).setPositiveButton(R.string.save,(dialog,which)->{String label=input.getText().toString().trim().replaceAll("[^\\p{L}\\p{N} _-]","_");if(!label.isEmpty()){File target=new File(file.getParentFile(),label+".wav");if(!target.equals(file)&&(target.exists()||!file.renameTo(target)))Toast.makeText(this,R.string.name_unavailable,Toast.LENGTH_LONG).show();}library();}).setNegativeButton(R.string.keep_name,(dialog,which)->library()).setOnCancelListener(dialog->library()).show();
 }
 private void library(){
  sessions.removeAllViews();File[] files=new File(getFilesDir(),"sessions").listFiles((dir,name)->name.endsWith(".wav"));
  if(files==null||files.length==0){LinearLayout empty=card(sessions);empty.addView(text(s(R.string.empty),15,muted));return;}
  Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
  for(File file:files){if(recording!=null&&recording.equals(file))continue;LinearLayout row=card(sessions);row.addView(text(s(R.string.session_title,file.getName().replace(".wav",""),Math.max(0,(file.length()-44)/96000)),16,fg));LinearLayout actions=new LinearLayout(this);row.addView(actions);
   button(s(R.string.play),actions,true).setOnClickListener(v->{if(engine.isRunning()){Toast.makeText(this,R.string.stop_first,Toast.LENGTH_LONG).show();return;}engine.start(file);listen.setText(R.string.stop);record.setEnabled(false);record.setAlpha(.45f);state.setText(s(R.string.playback,file.getName()));selectTab(0);});
   button(s(R.string.export_wav),actions,false).setOnClickListener(v->{export=file;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/wav").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,file.getName());try{startActivityForResult(intent,2);}catch(ActivityNotFoundException e){Toast.makeText(this,R.string.no_picker,Toast.LENGTH_LONG).show();}});
  }
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==2&&result==RESULT_OK&&data!=null&&data.getData()!=null&&export!=null){File source=export;Uri uri=data.getData();new Thread(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);ui.post(()->{if(!isDestroyed())Toast.makeText(this,R.string.export_done,Toast.LENGTH_SHORT).show();});}catch(IOException e){ui.post(()->{if(!isDestroyed())Toast.makeText(this,R.string.export_failed,Toast.LENGTH_LONG).show();});}}).start();}}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);}
 @Override protected void onPause(){super.onPause();engine.stop();}
 @Override protected void onDestroy(){engine.stop();ui.removeCallbacksAndMessages(null);((AudioManager)getSystemService(AUDIO_SERVICE)).unregisterAudioDeviceCallback(devices);super.onDestroy();}
 private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
 private GradientDrawable shape(int color){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(18));return shape;}
 private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(5),0,dp(5));return view;}
 private LinearLayout card(LinearLayout parent){LinearLayout card=column();card.setPadding(dp(20),dp(16),dp(20),dp(16));card.setBackground(shape(surface));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);parent.addView(card,params);return card;}
 private Button button(String label,LinearLayout parent,boolean primary){Button button=new Button(this);button.setText(label);button.setTextSize(15);button.setAllCaps(false);button.setTextColor(primary?INK:fg);button.setBackground(shape(primary?YELLOW:line));button.setBackgroundTintList(null);button.setMinHeight(dp(48));button.setPadding(dp(8),dp(8),dp(8),dp(8));boolean horizontal=parent.getOrientation()==LinearLayout.HORIZONTAL;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(horizontal?0:-1,-2,horizontal?1:0);params.setMargins(horizontal?dp(3):0,dp(10),horizontal?dp(3):0,0);parent.addView(button,params);return button;}
 private class Wave extends View{
  private short[] data=new short[0];private final Path trace=new Path();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Wave(Context context){super(context);setContentDescription(s(R.string.wave_description));}
  void update(short[] samples){data=samples;invalidate();}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setColor(line);paint.setStrokeWidth(dp(1));canvas.drawLine(0,getHeight()/2f,getWidth(),getHeight()/2f,paint);if(data.length<2)return;trace.reset();for(int i=0;i<data.length;i++){float x=i*getWidth()/(float)(data.length-1),y=getHeight()/2f-data[i]/32768f*getHeight()*.45f;if(i==0)trace.moveTo(x,y);else trace.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setColor(accent);paint.setStrokeWidth(dp(2));canvas.drawPath(trace,paint);paint.setStyle(Paint.Style.FILL);}
 }
}
