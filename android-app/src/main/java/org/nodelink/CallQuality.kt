package org.nodelink

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

// Measured call quality and the adaptive bitrate decision. Only real measurements are shown: round-trip time
// from ping/pong inside the call, playback delay, playback gaps (underruns) and frames dropped to keep delay low.
// The transport is TCP inside TLS, so network loss appears as delay and bursts, which the receiver turns into drops.
object CallQuality {
 private val LEVELS=intArrayOf(8000,12000,16000,24000,32000)
 @Volatile private var level=3;@Volatile private var good=0;@Volatile private var changes=0
 @Volatile private var rtt=-1L;@Volatile private var rttMax=0L;@Volatile private var rttSum=0L;@Volatile private var rttN=0
 @Volatile private var frames=0;@Volatile private var dropped=0;@Volatile private var delay=0;@Volatile private var gaps=0
 @Synchronized fun reset(){level=3;good=0;changes=0;rtt=-1;rttMax=0;rttSum=0;rttN=0;frames=0;dropped=0;delay=0;gaps=0}
 fun bitrate()=LEVELS[level]
 // Above 400 ms round trip: one step down at once. Below 150 ms three times in a row: one step up.
 @Synchronized fun onRtt(ms:Long){rtt=ms;rttMax=maxOf(rttMax,ms);rttSum+=ms;rttN++
  if(ms>400 && level>0){level--;good=0;changes++}else if(ms<150){good++;if(good>=3 && level<LEVELS.size-1){level++;good=0;changes++}}else good=0}
 fun frame(delayMs:Int,underruns:Int){frames++;delay=delayMs;gaps=underruns}
 fun drop(){dropped++}
 private fun pct()=if(frames==0)"0%" else "%.1f%%".format(Locale.US,100.0*dropped/frames)
 fun line()="Round trip ${if(rtt<0)"measuring" else "$rtt ms"} · ${bitrate()/1000} kbps · delay $delay ms · dropped ${pct()} · gaps $gaps"
 // Saved after every call so results can be reported per phone model; the last 10 are kept on the phone.
 fun save(c:Context,seconds:Long){if(frames==0)return
  val p=c.getSharedPreferences("calls",Context.MODE_PRIVATE);val at=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date())
  val row="$at · ${seconds}s · round trip avg ${if(rttN==0)"–" else "${rttSum/rttN}"} ms, max $rttMax ms · dropped ${pct()} of $frames frames · gaps $gaps · bitrate changes $changes, ended at ${bitrate()/1000} kbps"
  p.edit().putString("log",(listOf(row)+history(c)).take(10).joinToString("\n")).apply()}
 fun history(c:Context)=c.getSharedPreferences("calls",Context.MODE_PRIVATE).getString("log","")!!.lines().filter{it.isNotBlank()}
}