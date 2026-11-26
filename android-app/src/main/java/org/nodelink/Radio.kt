package org.nodelink

// Shared radio state: the background relay stays idle while the app is on screen, so the two never use Wi-Fi Direct at once.
object Radio {
 @Volatile var appVisible=false
 @Volatile var bg:Session?=null
}