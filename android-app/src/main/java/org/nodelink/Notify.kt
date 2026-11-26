package org.nodelink

import android.app.*
import android.content.*

// New-message alert while the app is not on screen (for example a message arriving over the background mesh relay).
// The text is not shown, so nothing readable appears on the lock screen.
object Notify {
 fun message(c:Context,peer:String){if(Radio.appVisible)return
  val nm=c.getSystemService(NotificationManager::class.java);if(!nm.areNotificationsEnabled())return
  nm.createNotificationChannel(NotificationChannel("messages","Messages",NotificationManager.IMPORTANCE_HIGH))
  val who="NL-"+peer.take(12).uppercase().chunked(4).joinToString("-")
  nm.notify(peer.hashCode(),Notification.Builder(c,"messages").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("New message").setContentText("From $who · open Nodelix to read")
   .setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,1,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)).build())
 }
 // Incoming call while the app is in the background (the ringtone plays either way).
 fun call(c:Context,peer:String){if(Radio.appVisible)return
  val nm=c.getSystemService(NotificationManager::class.java);if(!nm.areNotificationsEnabled())return
  nm.createNotificationChannel(NotificationChannel("calls","Calls",NotificationManager.IMPORTANCE_HIGH))
  nm.notify(9,Notification.Builder(c,"calls").setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Incoming Nodelix call")
   .setContentText("From NL-"+peer.take(12).uppercase().chunked(4).joinToString("-")+" · open to answer").setCategory(Notification.CATEGORY_CALL).setOngoing(true)
   .setContentIntent(PendingIntent.getActivity(c,2,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE)).build())
 }
 fun cancelCall(c:Context)=c.getSystemService(NotificationManager::class.java).cancel(9)
}