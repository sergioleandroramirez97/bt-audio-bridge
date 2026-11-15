package com.leandro.btbridge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Servicio en primer plano que hace de puente de audio por WiFi.
 *
 * Camino del audio:
 *   PC (Windows) --UDP--> [este servicio: AudioTrack] --Bluetooth A2DP--> auriculares
 *   auriculares --Bluetooth SCO--> [este servicio: AudioRecord] --UDP--> PC (Windows)
 *
 * NOTA: es un esqueleto funcional. Falta afinar buffers/jitter y manejar reconexión.
 * El micrófono Bluetooth usa SCO (startBluetoothSco), que en gama baja puede tardar en
 * levantar y tiene calidad limitada. Probar en el Moto E13 y ajustar.
 */
class AudioRelayService : Service() {

    @Volatile private var running = false
    // IP de la PC detectada automáticamente desde los paquetes de audio entrantes
    // (así no hace falta tipearla; con anclaje USB cambia según el DHCP del teléfono).
    @Volatile private var detectedPc: InetAddress? = null
    private var playbackThread: Thread? = null
    private var captureThread: Thread? = null
    private var audioManager: AudioManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        val pcIp = intent?.getStringExtra(EXTRA_PC_IP) ?: return START_NOT_STICKY

        startForeground(NOTIF_ID, buildNotification(pcIp))
        running = true

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Rutea el micro por Bluetooth (SCO). Puede necesitar unos segundos.
        audioManager?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            startBluetoothSco()
            isBluetoothScoOn = true
        }

        playbackThread = thread(name = "relay-playback") { runPlayback() }
        captureThread = thread(name = "relay-capture") { runCapture(pcIp) }
        return START_STICKY
    }

    /** Recibe PCM de la PC por UDP y lo reproduce (sale a los auriculares por A2DP). */
    private fun runPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(
            RelayProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(RelayProtocol.FRAME_BYTES * 8)   // buffer de jitter para evitar cortes

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RelayProtocol.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val socket = DatagramSocket(RelayProtocol.PORT_PC_TO_PHONE)
        socket.soTimeout = 1000
        val buf = ByteArray(RelayProtocol.FRAME_BYTES)
        val packet = DatagramPacket(buf, buf.size)

        track.play()
        try {
            while (running) {
                try {
                    socket.receive(packet)
                    if (detectedPc == null) detectedPc = packet.address
                    track.write(packet.data, 0, packet.length)
                } catch (_: java.net.SocketTimeoutException) {
                    // sin datos; seguimos esperando
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playback error", e)
        } finally {
            runCatching { track.stop(); track.release() }
            runCatching { socket.close() }
        }
    }

    /** Captura el micro de los auriculares (SCO) y lo manda a la PC por UDP. */
    @SuppressLint("MissingPermission")
    private fun runCapture(pcIp: String) {
        val minBuf = AudioRecord.getMinBufferSize(
            RelayProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(RelayProtocol.FRAME_BYTES * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // usa el micro ruteado por SCO
            RelayProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        val socket = DatagramSocket()
        val fallback: InetAddress? = pcIp.takeIf { it.isNotBlank() }
            ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
        val buf = ByteArray(RelayProtocol.FRAME_BYTES)

        record.startRecording()
        try {
            while (running) {
                val n = record.read(buf, 0, buf.size)
                // Preferimos la IP detectada del audio entrante; si aún no llegó, usamos la tipeada.
                val dest = detectedPc ?: fallback
                if (n > 0 && dest != null) {
                    socket.send(
                        DatagramPacket(buf, n, dest, RelayProtocol.PORT_PHONE_TO_PC)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture error", e)
        } finally {
            runCatching { record.stop(); record.release() }
            runCatching { socket.close() }
        }
    }

    override fun onDestroy() {
        running = false
        audioManager?.apply {
            isBluetoothScoOn = false
            stopBluetoothSco()
            mode = AudioManager.MODE_NORMAL
        }
        super.onDestroy()
    }

    private fun buildNotification(pcIp: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Puente de audio", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Puente de audio activo")
            .setContentText("Conectado con PC $pcIp")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AudioRelayService"
        private const val CHANNEL = "relay"
        private const val NOTIF_ID = 1
        const val EXTRA_PC_IP = "pc_ip"

        fun start(ctx: Context, pcIp: String) {
            val i = Intent(ctx, AudioRelayService::class.java).putExtra(EXTRA_PC_IP, pcIp)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AudioRelayService::class.java))
        }

        @Suppress("unused")
        fun bluetoothReady(): Boolean =
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    }
}
