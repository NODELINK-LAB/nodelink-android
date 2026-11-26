package org.nodelink

import android.app.Activity
import android.media.*
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*

// Call screen: contact avatar and name, call state and timer, incoming call (ringtone, Accept, Decline),
// mute and loudspeaker toggles and the measured call history. Audio routing goes back to normal when the call ends.
@Suppress("DEPRECATION")
class CallControls(private val a:Activity){
 private val am=a.getSystemService(AudioManager::class.java);private val h=Handler(Looper.getMainLooper());private var since=0L;private var tone:Ringtone?=null
 private val face=Ui.avatar(a,"?",96);private val who=Ui.title(a,"No contact selected",22f).apply{gravity=Gravity.CENTER;setPadding(0,Ui.dp(a,14),0,Ui.dp(a,4))}
 private val clock=Ui.caption(a,"").apply{gravity=Gravity.CENTER};private val incoming=Ui.title(a,"",16f).apply{gravity=Gravity.CENTER;setTextColor(Ui.BRAND);setPadding(0,Ui.dp(a,12),0,Ui.dp(a,4))}
 private val log=LinearLayout(a).apply{orientation=1}
 private fun refreshHistory(){log.removeAllViews();val history=CallQuality.history(a);if(history.isEmpty())log.addView(Ui.caption(a,"No measured calls yet"))
  history.forEach{entry->log.addView(Ui.card(a).apply{addView(Ui.title(a,entry.substringBefore(" · "),15f));addView(Ui.caption(a,entry.substringAfter(" · ")).apply{setTextIsSelectable(true)})})}}
 private val answer=Ui.button(Button(a).apply{text="Accept"},Ui.GREEN);private val reject=Ui.button(Button(a).apply{text="Decline"},Ui.RED)
 private val ringRow=LinearLayout(a).apply{orientation=1;visibility=View.GONE;addView(incoming,Ui.lp());addView(Ui.row(a,answer,reject))}
 private val mute=Button(a).apply{setOnClickListener{VoiceCall.muted=!VoiceCall.muted;show()}}
 private val speaker=Button(a).apply{setOnClickListener{am.isSpeakerphoneOn=!am.isSpeakerphoneOn;show()}}
 private val tick=object:Runnable{override fun run(){val s=(SystemClock.elapsedRealtime()-since)/1000;clock.text="On call %02d:%02d\n".format(s/60,s%60)+CallQuality.line();h.postDelayed(this,1000)}}
 fun header(box:LinearLayout){box.addView(LinearLayout(a).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;setPadding(0,Ui.dp(a,24),0,Ui.dp(a,16))
  addView(face);addView(who);addView(clock,Ui.lp());addView(ringRow,Ui.lp())})}
 fun peer(fp:String){if(!fp.matches(Regex("[0-9a-f]{64}"))){Ui.paint(face,"?");who.text="No contact selected";return}
  val n=ContactControls.name(a,fp);Ui.paint(face,n?:"#");who.text=n?:Ui.nl(fp)}
 fun addTo(box:LinearLayout){box.addView(Ui.row(a,mute,speaker));box.addView(Ui.section(a,"Recent Calls"));box.addView(log);ended()}
 fun onAnswer(accept:()->Unit,decline:()->Unit){answer.setOnClickListener{accept()};reject.setOnClickListener{decline()}}
 fun ringing(peer:String){this.peer(peer);incoming.text="Incoming call";ringRow.visibility=View.VISIBLE
  tone?.stop();tone=RingtoneManager.getRingtone(a,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply{play()};Notify.call(a,peer)}
 fun ringStop(){tone?.stop();tone=null;ringRow.visibility=View.GONE;Notify.cancelCall(a)}
 fun live(){ringStop();since=SystemClock.elapsedRealtime();VoiceCall.muted=false;am.mode=AudioManager.MODE_IN_COMMUNICATION;mute.isEnabled=true;speaker.isEnabled=true;show();h.removeCallbacks(tick);h.post(tick)}
 fun ended(){ringStop();h.removeCallbacks(tick);if(since>0){CallQuality.save(a,(SystemClock.elapsedRealtime()-since)/1000);since=0}
  refreshHistory();VoiceCall.muted=false;am.isSpeakerphoneOn=false;am.mode=AudioManager.MODE_NORMAL;mute.isEnabled=false;speaker.isEnabled=false;clock.text="Not in a call";show()}
 private fun show(){mute.text=if(VoiceCall.muted)"🔇  Unmute" else "🎤  Mute";speaker.text=if(am.isSpeakerphoneOn)"🔊  Speaker on" else "🔈  Speaker"
  Ui.button(mute,if(VoiceCall.muted)Ui.BRAND else Ui.RAISED);Ui.button(speaker,if(am.isSpeakerphoneOn)Ui.BRAND else Ui.RAISED)
  mute.alpha=if(mute.isEnabled)1f else .45f;speaker.alpha=if(speaker.isEnabled)1f else .45f}
}