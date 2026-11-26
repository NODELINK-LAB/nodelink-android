package org.nodelink

import android.media.*
import java.io.*
import java.nio.*

// Full-duplex Opus call inside an already pinned mutual-TLS session. Android 10+ codecs.
// Wire: [u16 length][Opus frame] for audio (1..1275 bytes); 0xFFFF + u64 = ping, 0xFFFE + u64 = pong (round-trip time).
// Quality: the sender lowers or raises its bitrate from the measured round trip (CallQuality); the receiver keeps
// playback delay under 120 ms by dropping frames that arrive in a burst instead of letting delay build up.
object VoiceCall {
 private const val RATE=48000; private const val FRAME=1920; private const val MAGIC=0x4e4c0302
 private const val PING=0xFFFF; private const val PONG=0xFFFE; private const val MAX_DELAY_MS=120
 @Volatile var muted=false   // muted calls still send silent frames, so the link stays alive
 fun supported()=MediaCodecList(MediaCodecList.REGULAR_CODECS).let{l->val f=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS,RATE,1);l.findEncoderForFormat(f)!=null && l.findDecoderForFormat(f)!=null}
 private fun le(n:Int)=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n*1_000_000L/RATE*1000).flip() as ByteBuffer
 private fun head()=ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).put("OpusHead".toByteArray()).put(1).put(1).putShort(312).putInt(RATE).putShort(0).put(0).flip() as ByteBuffer
 private fun encFormat(bps:Int)=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS,RATE,1).apply{setInteger(MediaFormat.KEY_BIT_RATE,bps)}
 fun run(i:DataInputStream,o:DataOutputStream){
  require(supported()){"This phone has no Opus codec"}
  o.writeInt(MAGIC);o.flush();require(i.readInt()==MAGIC){"The other phone runs a different Nodelix version"}
  CallQuality.reset()
  val mic=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(FRAME*4,AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)))
  val fx=listOfNotNull<android.media.audiofx.AudioEffect>(if(android.media.audiofx.AcousticEchoCanceler.isAvailable())android.media.audiofx.AcousticEchoCanceler.create(mic.audioSessionId) else null,if(android.media.audiofx.NoiseSuppressor.isAvailable())android.media.audiofx.NoiseSuppressor.create(mic.audioSessionId) else null).onEach{runCatching{it.enabled=true}}
  val speaker=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(FRAME*8).build()
  val enc=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);val dec=MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
  enc.configure(encFormat(CallQuality.bitrate()),null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
  dec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS,RATE,1).apply{setByteBuffer("csd-0",head());setByteBuffer("csd-1",le(312));setByteBuffer("csd-2",ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(80_000_000L).flip() as ByteBuffer)},null,null,0)
  val failed=java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
  val sender=Thread{runCatching{val pcm=ByteArray(FRAME);val info=MediaCodec.BufferInfo();var t=0L;var lastPing=0L;var bps=CallQuality.bitrate()
   while(!Thread.currentThread().isInterrupted){val n=mic.read(pcm,0,FRAME);if(n<=0)error("Microphone read failed: $n");if(muted)java.util.Arrays.fill(pcm,0,n,0.toByte())
    if(CallQuality.bitrate()!=bps){bps=CallQuality.bitrate();enc.stop();enc.configure(encFormat(bps),null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);enc.start()}   // adaptive bitrate
    val now=System.nanoTime();if(now-lastPing>2_000_000_000L){lastPing=now;synchronized(o){o.writeShort(PING);o.writeLong(now);o.flush()}}
    val ix=enc.dequeueInputBuffer(10000);if(ix>=0){enc.getInputBuffer(ix)!!.apply{clear();put(pcm,0,n)};enc.queueInputBuffer(ix,0,n,t,0);t+=n/2*1_000_000L/RATE}
    while(true){val ox=enc.dequeueOutputBuffer(info,0);if(ox<0)break
     if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0 && info.size in 1..1275){val b=ByteArray(info.size);enc.getOutputBuffer(ox)!!.apply{position(info.offset);get(b)};synchronized(o){o.writeShort(b.size);o.write(b);o.flush()}}
     enc.releaseOutputBuffer(ox,false)}}}.onFailure{failed.set(it)}}
  try{enc.start();dec.start();mic.startRecording();speaker.play();sender.start()
   val info=MediaCodec.BufferInfo();var t=0L;var written=0L
   while(true){failed.get()?.let{throw it};val n=i.readUnsignedShort()
    if(n==PING){val ts=i.readLong();synchronized(o){o.writeShort(PONG);o.writeLong(ts);o.flush()};continue}
    if(n==PONG){CallQuality.onRtt((System.nanoTime()-i.readLong())/1_000_000);continue}
    require(n in 1..1275){"Corrupt voice frame"};val b=ByteArray(n);i.readFully(b)
    val delay=((written-(speaker.playbackHeadPosition.toLong() and 0xFFFFFFFFL))*1000/RATE).toInt().coerceAtLeast(0)
    CallQuality.frame(delay,speaker.underrunCount);if(delay>MAX_DELAY_MS){CallQuality.drop();continue}
    val ix=dec.dequeueInputBuffer(5000);if(ix>=0){dec.getInputBuffer(ix)!!.apply{clear();put(b)};dec.queueInputBuffer(ix,0,n,t,0);t+=20000}
    while(true){val ox=dec.dequeueOutputBuffer(info,0);if(ox<0)break;if(info.size>0){val pcm=ByteArray(info.size);dec.getOutputBuffer(ox)!!.apply{position(info.offset);get(pcm)}
     val w=speaker.write(pcm,0,pcm.size,AudioTrack.WRITE_NON_BLOCKING);if(w>0)written+=w/2;if(w<pcm.size)CallQuality.drop()};dec.releaseOutputBuffer(ox,false)}}
  }finally{sender.interrupt();runCatching{mic.stop()};fx.forEach{it.release()};mic.release();speaker.release();sender.join(1000);runCatching{enc.stop()};enc.release();runCatching{dec.stop()};dec.release()}
 }
}