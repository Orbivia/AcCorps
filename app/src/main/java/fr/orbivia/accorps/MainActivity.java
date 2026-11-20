package fr.orbivia.accorps;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private static final int YELLOW=0xFFFFCC32,INK=0xFF25323C;
 private int bg,fg,muted,surface,line,accent;
 private AudioEngine engine;
 private final ScopeHistory history=new ScopeHistory(20000);
 private ScopeView scope;
 private LinearLayout scopePanel,recordingsPanel,sessions;
 private TextView state,timer,displayInfo,levelInfo;
 private ProgressBar inputMeter;
 private Button compareButton;
 private Button[] quickModes;
 private long lastMeterUpdate;
 private Button listen,record,stop,back;
 private File recording,export;
 private boolean playback,showingRecordings;
 private long recordingStartFrame;
 private double scrollSpeed=1;
 private boolean envelopeDisplay=true;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService previews=Executors.newSingleThreadExecutor();
 private final AudioDeviceCallback devices=new AudioDeviceCallback(){@Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){if(engine!=null&&engine.isLiveListening())engine.stop();}};
 private final Runnable tick=new Runnable(){public void run(){
  if(engine!=null&&engine.isRunning()){
   if(!scope.isPaused()&&!showingRecordings)scope.invalidate();
   long frames=engine.processedFrames()-(engine.isRecording()?recordingStartFrame:0);
   timer.setText(time(Math.max(0,frames/48000)));
   long now=SystemClock.uptimeMillis();if(now-lastMeterUpdate>=110){lastMeterUpdate=now;updateMeter();}
  }ui.postDelayed(this,33);
 }};
 private String s(int id,Object...args){return getString(id,args);}
 @Override public void onCreate(Bundle savedState){
  super.onCreate(savedState);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  bg=dark?0xFF111820:0xFFF7F8FA;fg=dark?0xFFF2F4F6:INK;muted=dark?0xFFBAC5CE:0xFF586873;surface=dark?0xFF1C2732:Color.WHITE;line=dark?0xFF344452:0xFFE2E7EB;accent=dark?0xFFF4C928:0xFFAD8100;
  engine=new AudioEngine(this,new AudioEngine.Listener(){public void block(short[] data,int n){history.append(data,n);}public void ended(String error){ui.post(()->{if(isDestroyed()||isFinishing())return;listen.setText(R.string.listen);record.setText(R.string.record);record.setEnabled(false);record.setAlpha(.45f);styleRecording(false);stop.setVisibility(View.GONE);scope.setPaused(false);scope.invalidate();state.setText(error==null?s(R.string.ready):error);if(error!=null)Toast.makeText(MainActivity.this,error,Toast.LENGTH_LONG).show();if(compareButton!=null){engine.compareRaw=false;refreshCompare();}if(recording!=null){File saved=recording;recording=null;saveRecording(saved);}playback=false;});}});
  loadSettings();
  LinearLayout root=column();root.setBackgroundColor(bg);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(2),dp(12),dp(2));
  back=button("‹",header,false);back.setContentDescription(s(R.string.back_scope));back.setOnClickListener(v->showRecordings(false));back.setVisibility(View.GONE);
  ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.accorps_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  logo.setBackground(shape(Color.WHITE));logo.setClipToOutline(true);logo.setPadding(dp(1),dp(1),dp(1),dp(1));
  LinearLayout.LayoutParams logoParams=new LinearLayout.LayoutParams(dp(34),dp(34));logoParams.rightMargin=dp(9);header.addView(logo,logoParams);
  TextView brand=text(s(R.string.app_name),21,fg);brand.setTypeface(null,Typeface.BOLD);header.addView(brand);
  header.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));
  icon(header,android.R.drawable.ic_menu_info_details,R.string.terms_info,this::terms);icon(header,android.R.drawable.ic_menu_manage,R.string.settings_info,this::settings);
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(49)));
  state=text(s(R.string.connect),12,muted);state.setPadding(dp(18),0,dp(18),dp(7));state.setMaxLines(2);
  state.setEllipsize(TextUtils.TruncateAt.END);root.addView(state,new LinearLayout.LayoutParams(-1,dp(29)));
  FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));scopePanel=column();scopePanel.setPadding(dp(8),dp(2),dp(8),dp(4));content.addView(scopePanel,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);
  scopePanel.addView(modeRow,new LinearLayout.LayoutParams(-1,dp(40)));
  quickModes=new Button[]{
    choice(s(R.string.heart),modeRow,false),
    choice(s(R.string.lungs),modeRow,false),
    choice(s(R.string.custom),modeRow,false)
  };
  quickModes[0].setOnClickListener(v->quickPreset(false));
  quickModes[1].setOnClickListener(v->quickPreset(true));
  quickModes[2].setOnClickListener(v->settings());
  refreshQuickModes();
  LinearLayout chart=column();chart.setBackground(shape(surface));chart.setPadding(dp(10),0,dp(10),0);scopePanel.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout chartHeader=new LinearLayout(this);chartHeader.setGravity(Gravity.CENTER_VERTICAL);chart.addView(chartHeader,new LinearLayout.LayoutParams(-1,dp(34)));
  displayInfo=text("",11,muted);displayInfo.setGravity(Gravity.CENTER_VERTICAL);chartHeader.addView(displayInfo,new LinearLayout.LayoutParams(0,-2,1));
  compareButton=button(s(R.string.compare_filtered),chartHeader,false);compareButton.setTextSize(11);compareButton.setMinWidth(dp(104));
  compareButton.setOnClickListener(v->{engine.compareRaw=!engine.compareRaw;refreshCompare();});refreshCompare();
  LinearLayout meterRow=new LinearLayout(this);meterRow.setGravity(Gravity.CENTER_VERTICAL);chart.addView(meterRow,new LinearLayout.LayoutParams(-1,dp(20)));
  levelInfo=text(s(R.string.input_level_idle),11,muted);meterRow.addView(levelInfo,new LinearLayout.LayoutParams(dp(101),-2));
  inputMeter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);inputMeter.setMax(100);
  inputMeter.setProgressTintList(android.content.res.ColorStateList.valueOf(YELLOW));
  inputMeter.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(line));
  meterRow.addView(inputMeter,new LinearLayout.LayoutParams(0,dp(5),1));
  scope=new ScopeView(this,history,YELLOW,line,YELLOW);scope.setSpeed(scrollSpeed);scope.setEnvelopeMode(envelopeDisplay);
  scope.setOnClickListener(v->{scrollSpeed=scrollSpeed==2?1:2;scope.setSpeed(scrollSpeed);persistSettings();Toast.makeText(this,s(R.string.zoom_feedback,formatSpeed(scrollSpeed)),Toast.LENGTH_SHORT).show();});
  scope.setContentDescription(s(R.string.scope_tap_zoom));chart.addView(scope,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout transport=column();transport.setPadding(0,dp(3),0,0);scopePanel.addView(transport);
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);transport.addView(controls);
  listen=button(s(R.string.listen),controls,false);listen.setOnClickListener(v->listenAction());
  record=button(s(R.string.record),controls,true);record.setEnabled(false);record.setAlpha(.45f);styleRecording(false);record.setOnClickListener(v->recordAction());
  stop=button(s(R.string.stop),controls,false);stop.setOnClickListener(v->engine.stop());stop.setVisibility(View.GONE);
  for(Button b:new Button[]{listen,record,stop}){
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),1);p.setMargins(dp(3),dp(2),dp(3),dp(2));b.setLayoutParams(p);
  }
  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);transport.addView(footer);
  timer=text("00:00",20,fg);timer.setTypeface(null,Typeface.BOLD);timer.setPadding(dp(8),0,0,0);
  timer.setGravity(Gravity.CENTER_VERTICAL);footer.addView(timer,new LinearLayout.LayoutParams(0,dp(42),1));
  button(s(R.string.recordings),footer,false).setOnClickListener(v->showRecordings(true));
  recordingsPanel=column();recordingsPanel.setPadding(dp(16),dp(4),dp(16),dp(8));recordingsPanel.setVisibility(View.GONE);content.addView(recordingsPanel,new FrameLayout.LayoutParams(-1,-1));
  TextView heading=text(s(R.string.recordings),22,fg);heading.setTypeface(null,Typeface.BOLD);recordingsPanel.addView(heading);ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(false);sessions=column();list.addView(sessions);recordingsPanel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);root.requestApplyInsets();updateDisplay();library();((AudioManager)getSystemService(AUDIO_SERVICE)).registerAudioDeviceCallback(devices,ui);ui.post(tick);
 }
 private void listenAction(){
  if(engine.isRunning()){scope.setPaused(!scope.isPaused());listen.setText(scope.isPaused()?R.string.resume_scope:R.string.pause_scope);state.setText(scope.isPaused()?s(R.string.scope_paused):playback?s(R.string.playback,""):s(R.string.live_status));return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}start(null);
 }
 private void start(File file){history.clear();scope.setPaused(false);scope.invalidate();timer.setText("00:00");playback=file!=null;engine.start(file);refreshCompare();listen.setText(R.string.pause_scope);record.setEnabled(!playback);record.setAlpha(playback?.45f:1);stop.setVisibility(View.VISIBLE);state.setText(file==null?s(R.string.live_status):s(R.string.playback,file.getName().replace(".wav","")));showRecordings(false);}
 private void recordAction(){try{if(engine.isRecording()){engine.finishRecord();record.setText(R.string.record);styleRecording(false);File saved=recording;recording=null;saveRecording(saved);}else{File dir=new File(getFilesDir(),"sessions");if(!dir.exists()&&!dir.mkdirs())throw new IOException(s(R.string.err_save,"storage unavailable"));File target=new File(dir,"Session_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+".wav");engine.record(target);recording=target;recordingStartFrame=engine.processedFrames();timer.setText("00:00");record.setText(R.string.finish);styleRecording(true);}}catch(IOException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 private void showRecordings(boolean show){showingRecordings=show;scopePanel.setVisibility(show?View.GONE:View.VISIBLE);recordingsPanel.setVisibility(show?View.VISIBLE:View.GONE);back.setVisibility(show?View.VISIBLE:View.GONE);if(show)library();else scope.invalidate();}
 private void updateDisplay(){
  String processing=engine.compareRaw||!engine.settings.enabled?s(R.string.original):s(R.string.filtered);
  if(engine.pitchEnabled&&!engine.compareRaw&&engine.pitchFactor>1.01&&engine.pitchMix>0)
   processing=String.format(Locale.ROOT,"Pitch ×%.1f",engine.pitchFactor);
  displayInfo.setText(processing+" · "+s(envelopeDisplay?R.string.envelope:R.string.waveform)+" · "+formatSpeed(scrollSpeed)+"×");
 }
 private String formatSpeed(double value){return value==.5?"0.5":value==1?"1":value==2?"2":"4";}
 private void loadSettings(){android.content.SharedPreferences p=getPreferences(0);scrollSpeed=p.getFloat("scroll",1);if(scrollSpeed!=.5&&scrollSpeed!=1&&scrollSpeed!=2&&scrollSpeed!=4)scrollSpeed=1;envelopeDisplay=p.getBoolean("envelope",true);engine.settings=new Dsp.Settings(p.getFloat("low",(float)Dsp.HEART_LOW_HZ),p.getFloat("high",(float)Dsp.HEART_HIGH_HZ),p.getFloat("gain",0),p.getBoolean("enabled",true),p.getBoolean("notch",false),p.getBoolean("focused",false));engine.rawCapture=p.getBoolean("raw_capture",true);engine.pitchEnabled=p.getBoolean("pitch_enabled",false);engine.pitchFactor=Math.max(1,Math.min(5,p.getFloat("pitch_factor",3)));engine.pitchMix=Math.max(0,Math.min(100,p.getInt("pitch_mix",100)));try{engine.outputMode=AudioChannels.Mode.valueOf(p.getString("audio_output","LEFT"));}catch(IllegalArgumentException|NullPointerException ignored){engine.outputMode=AudioChannels.Mode.LEFT;}}
 private void persistSettings(){Dsp.Settings d=engine.settings;getPreferences(0).edit().putFloat("scroll",(float)scrollSpeed).putBoolean("envelope",envelopeDisplay).putFloat("low",(float)d.lowHz).putFloat("high",(float)d.highHz).putFloat("gain",(float)d.gainDb).putBoolean("enabled",d.enabled).putBoolean("notch",d.notch).putBoolean("focused",d.focused).putBoolean("raw_capture",engine.rawCapture).putBoolean("pitch_enabled",engine.pitchEnabled).putFloat("pitch_factor",(float)engine.pitchFactor).putInt("pitch_mix",engine.pitchMix).putString("audio_output",engine.outputMode.name()).apply();updateDisplay();refreshQuickModes();}
 private void refreshCompare(){
  if(compareButton==null)return;
  compareButton.setText(engine.compareRaw?R.string.compare_raw:engine.pitchEnabled&&engine.pitchFactor>1.01?R.string.compare_shifted:R.string.compare_filtered);
  selected(compareButton,engine.compareRaw);updateDisplay();
 }
 private void quickPreset(boolean lungs){
  Dsp.Settings d=engine.settings;
  engine.settings=new Dsp.Settings(lungs?Dsp.LUNGS_LOW_HZ:Dsp.HEART_LOW_HZ,
    lungs?Dsp.LUNGS_HIGH_HZ:Dsp.HEART_HIGH_HZ,d.gainDb,true,d.notch,d.focused);
  persistSettings();
 }
 private void refreshQuickModes(){
  if(quickModes==null)return;
  Dsp.Settings d=engine.settings;
  boolean lungs=d.enabled&&d.lowHz==Dsp.LUNGS_LOW_HZ&&d.highHz==Dsp.LUNGS_HIGH_HZ;
  boolean heart=d.enabled&&d.lowHz==Dsp.HEART_LOW_HZ&&(d.highHz==Dsp.HEART_HIGH_HZ||d.highHz==160);
  selected(quickModes[0],heart);selected(quickModes[1],lungs);selected(quickModes[2],!heart&&!lungs);
 }
 private void styleRecording(boolean active){if(record==null)return;record.setBackground(shape(active?0xFFE65A53:YELLOW));record.setTextColor(active?Color.WHITE:INK);if(timer!=null)timer.setTextColor(active?0xFFE65A53:fg);}
 private void updateMeter(){if(inputMeter==null||levelInfo==null)return;float in=engine.inputPeak;
  boolean clipped=in>=.985f;boolean limited=engine.limiterActive;
  inputMeter.setProgress(Math.min(100,Math.round(in*100)));
  inputMeter.setProgressTintList(android.content.res.ColorStateList.valueOf(clipped||limited?0xFFE65A53:YELLOW));
  int db=(int)Math.round(20*Math.log10(Math.max(in,.0001)));
  levelInfo.setText(clipped?s(R.string.input_clipped):limited?s(R.string.output_limited):s(R.string.input_db,db));
  levelInfo.setTextColor(clipped||limited?0xFFE65A53:muted);
 }
 private void settings(){
  LinearLayout container=column();
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(8),0,dp(8),dp(3));container.addView(tabs);
  LinearLayout panel=column();panel.setPadding(dp(16),dp(4),dp(16),dp(8));
  LinearLayout displayPanel=column(),audioPanel=column(),filtersPanel=column();
  panel.addView(displayPanel);panel.addView(audioPanel);panel.addView(filtersPanel);
  ScrollView scroller=new ScrollView(this);scroller.setVerticalScrollBarEnabled(false);scroller.addView(panel);
  container.addView(scroller,new LinearLayout.LayoutParams(-1,Math.min(dp(460),getResources().getDisplayMetrics().heightPixels-dp(220))));
  Button[] sectionButtons=new Button[3];
  int[] sectionLabels={R.string.section_display,R.string.section_audio,R.string.section_filters};
  for(int i=0;i<3;i++){final int index=i;sectionButtons[i]=choice(s(sectionLabels[i]),tabs,i==0);sectionButtons[i].setOnClickListener(v->{
   displayPanel.setVisibility(index==0?View.VISIBLE:View.GONE);
   audioPanel.setVisibility(index==1?View.VISIBLE:View.GONE);
   filtersPanel.setVisibility(index==2?View.VISIBLE:View.GONE);
   for(int j=0;j<3;j++)selected(sectionButtons[j],j==index);
   scroller.scrollTo(0,0);
  });}
  audioPanel.setVisibility(View.GONE);filtersPanel.setVisibility(View.GONE);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.scope_settings).setView(container).setPositiveButton(R.string.close,null).create();
  displayPanel.addView(text(s(R.string.scroll_speed),13,muted));LinearLayout speeds=new LinearLayout(this);displayPanel.addView(speeds);Button[] speedButtons=new Button[4];double[] choices={.5,1,2,4};
  for(int i=0;i<4;i++){final double value=choices[i];speedButtons[i]=choice(formatSpeed(value)+"×",speeds,scrollSpeed==value);speedButtons[i].setOnClickListener(v->{scrollSpeed=value;scope.setSpeed(value);persistSettings();for(int j=0;j<4;j++)selected(speedButtons[j],choices[j]==scrollSpeed);});}
  displayPanel.addView(text(s(R.string.graph_mode),13,muted));LinearLayout graphModes=new LinearLayout(this);displayPanel.addView(graphModes);
  Button envelopeButton=choice(s(R.string.envelope),graphModes,envelopeDisplay),waveformButton=choice(s(R.string.waveform),graphModes,!envelopeDisplay);
  envelopeButton.setOnClickListener(v->{envelopeDisplay=true;scope.setEnvelopeMode(true);selected(envelopeButton,true);selected(waveformButton,false);persistSettings();});
  waveformButton.setOnClickListener(v->{envelopeDisplay=false;scope.setEnvelopeMode(false);selected(envelopeButton,false);selected(waveformButton,true);persistSettings();});
  audioPanel.addView(text(s(R.string.audio_output),13,muted));LinearLayout outputs=new LinearLayout(this);audioPanel.addView(outputs);
  AudioChannels.Mode[] channelModes={AudioChannels.Mode.LEFT,AudioChannels.Mode.RIGHT,AudioChannels.Mode.STEREO};
  int[] channelLabels={R.string.left_only,R.string.right_only,R.string.stereo};Button[] outputButtons=new Button[3];
  for(int i=0;i<3;i++){final AudioChannels.Mode value=channelModes[i];outputButtons[i]=choice(s(channelLabels[i]),outputs,engine.outputMode==value);outputButtons[i].setOnClickListener(v->{engine.outputMode=value;persistSettings();for(int j=0;j<3;j++)selected(outputButtons[j],channelModes[j]==value);});}
  audioPanel.addView(text(s(R.string.capture_source),13,muted));LinearLayout captureRow=new LinearLayout(this);audioPanel.addView(captureRow);
  Button rawCaptureButton=choice(s(R.string.raw_capture),captureRow,engine.rawCapture),standardCaptureButton=choice(s(R.string.standard_capture),captureRow,!engine.rawCapture);
  rawCaptureButton.setOnClickListener(v->{engine.rawCapture=true;selected(rawCaptureButton,true);selected(standardCaptureButton,false);persistSettings();});
  standardCaptureButton.setOnClickListener(v->{engine.rawCapture=false;selected(rawCaptureButton,false);selected(standardCaptureButton,true);persistSettings();});
  filtersPanel.addView(text(s(R.string.preset),13,muted));
  LinearLayout profiles=new LinearLayout(this),profilesExtra=new LinearLayout(this);filtersPanel.addView(profiles);filtersPanel.addView(profilesExtra);
  Button[] presets=new Button[5];int[] names={R.string.heart_low,R.string.heart_high,R.string.lungs,R.string.raw,R.string.custom};
  Dsp.Settings initial=engine.settings;int preset=!initial.enabled?3:initial.lowHz==Dsp.HEART_LOW_HZ&&initial.highHz==160?0:initial.lowHz==Dsp.HEART_LOW_HZ&&initial.highHz==Dsp.HEART_HIGH_HZ?1:initial.lowHz==Dsp.LUNGS_LOW_HZ&&initial.highHz==Dsp.LUNGS_HIGH_HZ?2:4;
  for(int i=0;i<5;i++)presets[i]=choice(s(names[i]),i<3?profiles:profilesExtra,i==preset);
  TextView[] values=new TextView[3];
  Runnable refresh=()->{Dsp.Settings d=engine.settings;values[0].setText(s(R.string.hz_value,(int)d.lowHz));values[1].setText(s(R.string.hz_value,(int)d.highHz));values[2].setText(s(R.string.db_value,(int)d.gainDb));persistSettings();};
  Runnable manual=()->{for(int j=0;j<5;j++)selected(presets[j],j==(!engine.settings.enabled?3:4));refresh.run();};
  stepper(filtersPanel,R.string.high_pass_short,values,0,()->adjust(0,-5,manual),()->adjust(0,5,manual),()->enterValue(0,manual));
  stepper(panel,R.string.low_pass_short,values,1,()->adjust(1,-50,manual),()->adjust(1,50,manual),()->enterValue(1,manual));
  stepper(panel,R.string.gain_short,values,2,()->adjust(2,-1,manual),()->adjust(2,1,manual),()->enterValue(2,manual));
  LinearLayout enabledRow=settingRow(filtersPanel,s(R.string.filters_enabled));Button[] enabledButtons={choice(s(R.string.off),enabledRow,!engine.settings.enabled),choice(s(R.string.on),enabledRow,engine.settings.enabled)};
  LinearLayout notchRow=settingRow(filtersPanel,s(R.string.notch_short));Button[] notchButtons={choice(s(R.string.off),notchRow,!engine.settings.notch),choice(s(R.string.on),notchRow,engine.settings.notch)};
  for(int i=0;i<2;i++){final boolean on=i==1;enabledButtons[i].setOnClickListener(v->{Dsp.Settings d=engine.settings;engine.settings=new Dsp.Settings(d.lowHz,d.highHz,d.gainDb,on,d.notch,d.focused);selected(enabledButtons[0],!on);selected(enabledButtons[1],on);manual.run();});notchButtons[i].setOnClickListener(v->{Dsp.Settings d=engine.settings;engine.settings=new Dsp.Settings(d.lowHz,d.highHz,d.gainDb,d.enabled,on,d.focused);selected(notchButtons[0],!on);selected(notchButtons[1],on);manual.run();});}
  for(int i=0;i<5;i++){final int index=i;presets[i].setOnClickListener(v->{Dsp.Settings d=engine.settings;
   if(index<3){double lo=index==2?Dsp.LUNGS_LOW_HZ:Dsp.HEART_LOW_HZ,hi=index==0?160:index==1?Dsp.HEART_HIGH_HZ:Dsp.LUNGS_HIGH_HZ;engine.settings=new Dsp.Settings(lo,hi,d.gainDb,true,d.notch,d.focused);}
   else if(index==3){engine.settings=new Dsp.Settings(d.lowHz,d.highHz,d.gainDb,false,d.notch,d.focused);}
   selected(enabledButtons[0],!engine.settings.enabled);selected(enabledButtons[1],engine.settings.enabled);
   for(int j=0;j<5;j++)selected(presets[j],j==index);refresh.run();});}
  filtersPanel.addView(text(s(R.string.filter_character),13,muted));LinearLayout filterStyles=new LinearLayout(this);filtersPanel.addView(filterStyles);
  Button naturalButton=choice(s(R.string.natural),filterStyles,!engine.settings.focused),focusedButton=choice(s(R.string.focused),filterStyles,engine.settings.focused);
  naturalButton.setOnClickListener(v->{Dsp.Settings d=engine.settings;engine.settings=new Dsp.Settings(d.lowHz,d.highHz,d.gainDb,d.enabled,d.notch,false);selected(naturalButton,true);selected(focusedButton,false);persistSettings();});
  focusedButton.setOnClickListener(v->{Dsp.Settings d=engine.settings;engine.settings=new Dsp.Settings(d.lowHz,d.highHz,d.gainDb,d.enabled,d.notch,true);selected(naturalButton,false);selected(focusedButton,true);persistSettings();});
  button(s(R.string.reset),filtersPanel,false).setOnClickListener(v->{engine.settings=new Dsp.Settings(Dsp.HEART_LOW_HZ,Dsp.HEART_HIGH_HZ,0,true,false,false);engine.outputMode=AudioChannels.Mode.LEFT;engine.rawCapture=true;scrollSpeed=1;scope.setSpeed(1);envelopeDisplay=true;scope.setEnvelopeMode(true);persistSettings();dialog.dismiss();settings();});refresh.run();dialog.show();android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();dialog.getWindow().setLayout(Math.min(dp(440),metrics.widthPixels-dp(32)),Math.min(dp(650),metrics.heightPixels-dp(76)));
 }
 private void adjust(int field,int delta,Runnable refresh){Dsp.Settings d=engine.settings;double low=d.lowHz,high=d.highHz,gain=d.gainDb;if(field==0)low=Math.max(10,Math.min(Math.min(500,high-20),low+delta));else if(field==1)high=Math.max(Math.max(100,low+20),Math.min(4000,high+delta));else gain=Math.max(-12,Math.min(36,gain+delta));engine.settings=new Dsp.Settings(low,high,gain,d.enabled,d.notch,d.focused);refresh.run();}
 private void enterValue(int field,Runnable refresh){Dsp.Settings d=engine.settings;int minimum=field==0?10:field==1?(int)Math.max(100,d.lowHz+20):-12,maximum=field==0?(int)Math.min(500,d.highHz-20):field==1?4000:36;EditText input=new EditText(this);input.setSingleLine();input.setInputType(InputType.TYPE_CLASS_NUMBER|(field==2?InputType.TYPE_NUMBER_FLAG_SIGNED:0));input.setText(String.valueOf((int)(field==0?d.lowHz:field==1?d.highHz:d.gainDb)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(s(R.string.value_title,s(field==0?R.string.high_pass_short:field==1?R.string.low_pass_short:R.string.gain_short))).setView(input).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{int value=Integer.parseInt(input.getText().toString());if(value<minimum||value>maximum)throw new NumberFormatException();Dsp.Settings old=engine.settings;engine.settings=new Dsp.Settings(field==0?value:old.lowHz,field==1?value:old.highHz,field==2?value:old.gainDb,old.enabled,old.notch,old.focused);refresh.run();dialog.dismiss();}catch(NumberFormatException e){input.setError(s(R.string.value_invalid,minimum,maximum));}}));dialog.show();}
 private LinearLayout settingRow(LinearLayout panel,String label){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);panel.addView(row);TextView title=text(label,14,fg);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));return row;}
 private void stepper(LinearLayout panel,int label,TextView[] values,int index,Runnable minus,Runnable plus,Runnable edit){LinearLayout row=settingRow(panel,s(label));button("−",row,false).setOnClickListener(v->minus.run());Button value=button("",row,false);values[index]=value;value.setMinWidth(dp(86));value.setContentDescription(s(label));value.setOnClickListener(v->edit.run());button("+",row,false).setOnClickListener(v->plus.run());}
 private void terms(){
  LinearLayout page=column();page.setPadding(dp(16),dp(4),dp(16),dp(10));
  String version="?";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
  page.addView(text(s(R.string.app_name)+" "+version+" · Orbivia",14,muted));
  TextView warning=text(s(R.string.terms_warning),14,fg);warning.setPadding(0,dp(10),0,dp(12));page.addView(warning);
  int[] headings={R.string.terms_purpose_title,R.string.terms_listening_title,R.string.terms_operation_title,R.string.terms_privacy_title,R.string.terms_export_title,R.string.terms_contact_title};
  int[] bodies={R.string.terms_purpose_content,R.string.terms_listening_content,R.string.terms_operation_content,R.string.terms_privacy_content,R.string.terms_export_content,R.string.terms_contact_content};
  for(int i=0;i<headings.length;i++){final TextView body=text(s(bodies[i]),14,fg);
   body.setPadding(dp(8),dp(5),dp(5),dp(15));body.setVisibility(i==0?View.VISIBLE:View.GONE);body.setTextIsSelectable(true);
   final String headingTitle=s(headings[i]);Button heading=button((i==0?"▾  ":"▸  ")+headingTitle,page,false);
   heading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
   heading.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);
    heading.setText((open?"▾  ":"▸  ")+headingTitle);});page.addView(body);
  }
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(page);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.terms_title).setView(scroll).setPositiveButton(R.string.close,null).create();
  dialog.show();android.util.DisplayMetrics m=getResources().getDisplayMetrics();
  dialog.getWindow().setLayout(Math.min(dp(440),m.widthPixels-dp(32)),Math.min(dp(670),m.heightPixels-dp(76)));
 }
 private void saveRecording(File file){if(file==null||!file.exists())return;
  try{if(Wav.dataBytes(file)==0){Toast.makeText(this,R.string.empty_recording,Toast.LENGTH_LONG).show();library();return;}}
  catch(IOException e){Toast.makeText(this,R.string.invalid_recording,Toast.LENGTH_LONG).show();library();return;}if(isDestroyed()||isFinishing()||!hasWindowFocus()){library();return;}LinearLayout panel=column();panel.setPadding(dp(20),0,dp(20),0);panel.addView(text(s(R.string.duration,time(Math.max(0,(file.length()-44)/96000))),13,muted));panel.addView(text(s(R.string.session_name),14,fg));EditText name=new EditText(this);name.setSingleLine();name.setText(file.getName().replace(".wav",""));panel.addView(name);panel.addView(text(s(R.string.raw_saved),12,muted));new AlertDialog.Builder(this).setTitle(R.string.save_recording).setView(panel).setPositiveButton(R.string.save,(d,w)->{rename(file,name.getText().toString());library();}).setNeutralButton(R.string.keep_name,(d,w)->library()).setNegativeButton(R.string.discard,(d,w)->new AlertDialog.Builder(this).setMessage(R.string.discard_question).setNegativeButton(R.string.cancel,(a,b)->library()).setPositiveButton(R.string.discard,(a,b)->{if(!file.delete())Toast.makeText(this,R.string.export_failed,Toast.LENGTH_SHORT).show();library();}).show()).setOnCancelListener(d->library()).show();}
 private void rename(File file,String value){String label=value.trim().replaceAll("[^\\p{L}\\p{N} _-]","_");if(label.isEmpty())return;File target=new File(file.getParentFile(),label+".wav");if(!target.equals(file)&&(target.exists()||!file.renameTo(target)))Toast.makeText(this,R.string.name_unavailable,Toast.LENGTH_LONG).show();}
 private void library(){
  sessions.removeAllViews();
  File[] files=new File(getFilesDir(),"sessions").listFiles((dir,name)->name.endsWith(".wav"));
  if(files==null||files.length==0){
   TextView emptyIcon=text("◎",40,YELLOW);emptyIcon.setGravity(Gravity.CENTER);emptyIcon.setPadding(0,dp(48),0,dp(10));sessions.addView(emptyIcon);
   TextView title=text(s(R.string.empty_title),18,fg);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);sessions.addView(title);
   TextView hint=text(s(R.string.empty),14,muted);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(12),dp(8),dp(12),0);sessions.addView(hint);return;
  }
  Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
  for(File file:files){
   if(recording!=null&&file.equals(recording))continue;
   LinearLayout card=column();card.setPadding(dp(12),dp(10),dp(12),dp(8));card.setBackground(shape(surface));
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);sessions.addView(card,cp);
   LinearLayout summary=new LinearLayout(this);summary.setGravity(Gravity.CENTER_VERTICAL);card.addView(summary,new LinearLayout.LayoutParams(-1,dp(54)));
   LinearLayout description=column();summary.addView(description,new LinearLayout.LayoutParams(0,-2,1));
   TextView name=text(file.getName().replace(".wav",""),15,fg);name.setSingleLine(true);
   name.setEllipsize(TextUtils.TruncateAt.MIDDLE);name.setTypeface(null,Typeface.BOLD);description.addView(name);
   long bytes=-1;try{bytes=Wav.dataBytes(file);}catch(IOException ignored){}
   final boolean available=bytes>0;
   String recordedDate=new SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.getDefault()).format(new Date(file.lastModified()));
   description.addView(text(available?s(R.string.recording_with_date,recordedDate,time(bytes/96000)):s(R.string.invalid_recording),12,muted));
   MiniWave mini=new MiniWave(this);summary.addView(mini,new LinearLayout.LayoutParams(dp(104),dp(42)));
   if(available)loadPreview(file,mini);
   LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);card.addView(actions);
   Button playButton=button(s(R.string.play),actions,true);playButton.setEnabled(available);
   LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(44),1);pp.setMargins(dp(2),dp(2),dp(2),dp(2));playButton.setLayoutParams(pp);
   playButton.setOnClickListener(v->{if(engine.isRunning()){Toast.makeText(this,R.string.stop_first,Toast.LENGTH_LONG).show();return;}start(file);});
   Button exportButton=button(s(R.string.export_wav),actions,false);exportButton.setEnabled(available);
   LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(44),1);ep.setMargins(dp(2),dp(2),dp(2),dp(2));exportButton.setLayoutParams(ep);
   exportButton.setOnClickListener(v->export(file));
   Button more=button("⋮",actions,false);more.setContentDescription(s(R.string.more_actions,file.getName()));
   more.setOnClickListener(v->{
    PopupMenu menu=new PopupMenu(this,more);
    menu.getMenu().add(R.string.rename).setOnMenuItemClickListener(item->{
     EditText input=new EditText(this);input.setSingleLine();input.setText(file.getName().replace(".wav",""));
     new AlertDialog.Builder(this).setTitle(R.string.rename).setView(input)
      .setPositiveButton(R.string.save,(a,b)->{rename(file,input.getText().toString());library();})
      .setNegativeButton(R.string.cancel,null).show();return true;
    });
    menu.getMenu().add(R.string.delete).setOnMenuItemClickListener(item->{
     new AlertDialog.Builder(this).setMessage(R.string.delete_question)
      .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delete,(a,b)->{file.delete();library();}).show();return true;
    });
    menu.show();
   });
  }
 }
 private void loadPreview(File file,MiniWave view){previews.execute(()->{float[] values=new float[80];try(RandomAccessFile in=new RandomAccessFile(file,"r")){long samples=Math.max(0,(in.length()-44)/2);for(int i=0;i<values.length;i++){if(Thread.currentThread().isInterrupted())return;long offset=samples*i/values.length;in.seek(44+offset*2);int peak=0;for(int j=0;j<48&&offset+j<samples;j++){int lo=in.read(),hi=in.read();if(lo<0||hi<0)break;peak=Math.max(peak,Math.abs((int)(short)(lo|(hi<<8))));}values[i]=peak;}}catch(IOException ignored){}ui.post(()->{if(!isDestroyed()){view.values=values;view.invalidate();}});});}
 private void export(File file){
  try{if(Wav.dataBytes(file)==0){Toast.makeText(this,R.string.empty_recording,Toast.LENGTH_LONG).show();return;}}
  catch(IOException e){Toast.makeText(this,R.string.invalid_recording,Toast.LENGTH_LONG).show();return;}
  export=file;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/wav").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,file.getName());try{startActivityForResult(intent,2);}catch(ActivityNotFoundException e){Toast.makeText(this,R.string.no_picker,Toast.LENGTH_LONG).show();}}
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=2||result!=RESULT_OK||data==null||data.getData()==null||export==null)return;
  File source=export;export=null;Uri destination=data.getData();
  Toast.makeText(this,R.string.export_in_progress,Toast.LENGTH_SHORT).show();
  new Thread(()->{
   boolean success=false;String reason="Unknown error";
   try{
    if(Wav.dataBytes(source)==0)throw new IOException("No audio samples");
    long expected=source.length(),written=0;
    try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(destination,"wt")){
     if(out==null)throw new IOException("Destination is unavailable");
     byte[] buffer=new byte[8192];int n;
     while((n=in.read(buffer))!=-1){out.write(buffer,0,n);written+=n;}
     out.flush();
    }
    if(written!=expected)throw new IOException("Incomplete copy: "+written+" of "+expected+" bytes");
    success=true;
   }catch(Exception e){reason=e.getMessage();}
   if(!success){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}}
   final boolean ok=success;final String message=reason;
   ui.post(()->Toast.makeText(this,ok?s(R.string.export_done):s(R.string.export_failed_detail,message),Toast.LENGTH_LONG).show());
  },"AcCorps-export").start();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==1&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)start(null);else state.setText(R.string.mic_permission);}
 @Override protected void onPause(){super.onPause();engine.stop();}
 @Override protected void onDestroy(){engine.stop();ui.removeCallbacksAndMessages(null);previews.shutdownNow();((AudioManager)getSystemService(AUDIO_SERVICE)).unregisterAudioDeviceCallback(devices);super.onDestroy();}
 private String time(long seconds){return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
 private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
 private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(3),0,dp(3));return view;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
 private Button button(String label,LinearLayout parent,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(dp(44));b.setMinimumWidth(dp(44));b.setMinHeight(dp(44));b.setPadding(dp(10),0,dp(10),0);b.setTextColor(primary?INK:fg);b.setBackgroundTintList(null);b.setBackground(shape(primary?YELLOW:line));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.VERTICAL?-1:-2,dp(44));p.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(b,p);return b;}
 private Button choice(String label,LinearLayout parent,boolean active){Button b=button(label,parent,active);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));b.setLayoutParams(p);return b;}
 private void selected(Button b,boolean active){b.setBackground(shape(active?YELLOW:line));b.setTextColor(active?INK:fg);b.setSelected(active);}
 private void icon(LinearLayout parent,int resource,int description,Runnable action){ImageButton b=new ImageButton(this);b.setImageResource(resource);b.setColorFilter(fg);b.setBackgroundColor(Color.TRANSPARENT);b.setContentDescription(s(description));b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));}
 private class MiniWave extends View{float[] values;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);MiniWave(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}@Override protected void onDraw(Canvas c){if(values==null)return;float max=1;for(float value:values)max=Math.max(max,value);paint.setColor(fg);paint.setStrokeWidth(Math.max(1,getResources().getDisplayMetrics().density));for(int i=0;i<values.length;i++){float x=i*getWidth()/(float)values.length,h=values[i]/max*getHeight()*.45f;c.drawLine(x,getHeight()/2f-h,x,getHeight()/2f+h,paint);}}}
}
