package org.nodelink

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.View
import android.webkit.*

// Explorer tab: the NODELINK explorer and console from nodelink.base44.app, shown inside the app.
// Links to other sites (for example the APK update) open outside the app.
@SuppressLint("SetJavaScriptEnabled")
class WebScreen(private val a:Activity){
 val view=WebView(a).apply{visibility=View.GONE;setBackgroundColor(Ui.BG);settings.javaScriptEnabled=true;settings.domStorageEnabled=true
  webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean{
   if(r.url.host==HOST)return false;runCatching{a.startActivity(Intent(Intent.ACTION_VIEW,r.url))};return true}}}
 fun open(){if(view.url==null)view.loadUrl("https://$HOST/explorer")}
 fun back():Boolean{if(view.visibility==View.VISIBLE && view.canGoBack()){view.goBack();return true};return false}
 companion object{const val HOST="nodelink.base44.app";const val APK="https://github.com/NODELINK-LAB/nodelink-android/releases/download/latest/NODELINK.apk"}
}