package com.leandro.btbridge

/**
 * Parámetros del transporte de audio por WiFi (UDP).
 *
 * El companion de Windows debe usar EXACTAMENTE estos valores (ver windows/audio_relay_pc.py).
 * Audio PCM 16-bit, mono, 16 kHz: suficiente para voz y liviano para la red y el Moto E13.
 * Si querés música en estéreo/48 kHz, subí SAMPLE_RATE y CHANNELS en ambos lados.
 */
object RelayProtocol {
    const val SAMPLE_RATE = 16_000        // Hz
    const val CHANNELS = 1                // mono
    const val BYTES_PER_SAMPLE = 2        // PCM 16-bit

    // Puertos UDP (el teléfono escucha PLAYBACK y manda MIC hacia la IP de la PC)
    const val PORT_PC_TO_PHONE = 45001    // audio del sistema de Windows -> auriculares
    const val PORT_PHONE_TO_PC = 45002    // micrófono de auriculares -> Windows

    const val FRAME_SAMPLES = 320         // 20 ms a 16 kHz
    const val FRAME_BYTES = FRAME_SAMPLES * BYTES_PER_SAMPLE
}
