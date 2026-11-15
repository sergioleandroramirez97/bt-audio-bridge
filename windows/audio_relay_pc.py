"""
Companion de Windows para BT Audio Bridge.

Qué hace:
  1) Captura el audio del sistema de Windows (WASAPI loopback) y lo manda al teléfono
     por UDP -> el teléfono lo reproduce en los auriculares Bluetooth.
  2) Recibe el micrófono de los auriculares (UDP desde el teléfono) y lo inyecta en un
     "micrófono virtual" (VB-CABLE) para que Zoom/Meet/Discord lo usen como entrada.

Requisitos:
  - Python 3.10+
  - pip install -r requirements.txt   (sounddevice + numpy)
  - VB-CABLE instalado (gratis): https://vb-audio.com/Cable/
    Para el micro virtual, en las apps elegí "CABLE Output" como micrófono.

Uso (anclaje USB; el teléfono como gateway suele ser 192.168.42.129):
  python audio_relay_pc.py
  python audio_relay_pc.py --phone-ip 192.168.42.129   # si tu caso difiere

Parámetros de audio: DEBEN coincidir con RelayProtocol.kt del lado Android.
"""
import argparse
import socket
import sys
import threading

import numpy as np
import sounddevice as sd

# --- Debe coincidir con RelayProtocol.kt ---
SAMPLE_RATE = 16_000
CHANNELS = 1
FRAME_SAMPLES = 320          # 20 ms
PORT_PC_TO_PHONE = 45001     # audio del sistema -> teléfono
PORT_PHONE_TO_PC = 45002     # micro del teléfono -> PC

stop_event = threading.Event()


def find_loopback_input():
    """Devuelve (device_index, native_rate, native_channels) del dispositivo de salida
    por defecto abierto en modo loopback (WASAPI)."""
    hostapis = sd.query_hostapis()
    wasapi = next((i for i, h in enumerate(hostapis) if "WASAPI" in h["name"]), None)
    if wasapi is None:
        sys.exit("No encontré WASAPI. Este script es solo para Windows.")
    default_out = sd.query_hostapis(wasapi)["default_output_device"]
    info = sd.query_devices(default_out)
    return default_out, int(info["default_samplerate"]), 2


def find_cable_output_device():
    """Busca el dispositivo de SALIDA 'CABLE Input (VB-Audio...)' para inyectar el micro."""
    for i, d in enumerate(sd.query_devices()):
        if d["max_output_channels"] > 0 and "CABLE Input" in d["name"]:
            return i
    return None


def downmix_resample(block: np.ndarray, src_rate: int) -> bytes:
    """Convierte audio float32 (frames, ch) a PCM16 mono a SAMPLE_RATE. Resample simple."""
    mono = block.mean(axis=1) if block.ndim > 1 else block
    if src_rate != SAMPLE_RATE:
        n_out = int(len(mono) * SAMPLE_RATE / src_rate)
        if n_out > 0:
            idx = np.linspace(0, len(mono) - 1, n_out)
            mono = np.interp(idx, np.arange(len(mono)), mono)
    pcm = np.clip(mono * 32767, -32768, 32767).astype("<i2")
    return pcm.tobytes()


def playback_to_phone(phone_ip: str):
    """Loopback del sistema -> UDP al teléfono."""
    dev, native_rate, _ = find_loopback_input()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    wasapi_loopback = sd.WasapiSettings(loopback=True)
    print(f"[PC->tel] Capturando salida del sistema @ {native_rate} Hz -> {phone_ip}:{PORT_PC_TO_PHONE}")

    def cb(indata, frames, t, status):
        if status:
            print("loopback:", status, file=sys.stderr)
        data = downmix_resample(indata.copy(), native_rate)
        # fragmentar en frames de 20 ms
        step = FRAME_SAMPLES * 2
        for off in range(0, len(data), step):
            sock.sendto(data[off:off + step], (phone_ip, PORT_PC_TO_PHONE))

    with sd.InputStream(device=dev, channels=2, samplerate=native_rate,
                        dtype="float32", extra_settings=wasapi_loopback, callback=cb):
        stop_event.wait()
    sock.close()


def mic_from_phone():
    """UDP desde el teléfono -> micrófono virtual (CABLE Input)."""
    cable = find_cable_output_device()
    if cable is None:
        print("[tel->PC] No encontré VB-CABLE. Instalalo para tener micrófono virtual. "
              "Por ahora solo escucho el micro sin reinyectarlo.", file=sys.stderr)

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", PORT_PHONE_TO_PC))
    sock.settimeout(1.0)
    print(f"[tel->PC] Escuchando micro en UDP :{PORT_PHONE_TO_PC}"
          + (f" -> inyectando en dispositivo {cable}" if cable is not None else ""))

    stream = None
    if cable is not None:
        stream = sd.OutputStream(device=cable, channels=CHANNELS,
                                 samplerate=SAMPLE_RATE, dtype="int16")
        stream.start()

    try:
        while not stop_event.is_set():
            try:
                data, _ = sock.recvfrom(4096)
            except socket.timeout:
                continue
            if stream is not None:
                samples = np.frombuffer(data, dtype="<i2").reshape(-1, 1)
                stream.write(samples)
    finally:
        if stream is not None:
            stream.stop(); stream.close()
        sock.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--phone-ip", default="192.168.42.129",
                    help="IP del teléfono (gateway del anclaje USB; por defecto 192.168.42.129)")
    args = ap.parse_args()

    t1 = threading.Thread(target=playback_to_phone, args=(args.phone_ip,), daemon=True)
    t2 = threading.Thread(target=mic_from_phone, daemon=True)
    t1.start(); t2.start()
    print("Puente activo. Ctrl+C para salir.")
    try:
        while True:
            t1.join(0.5)
    except KeyboardInterrupt:
        stop_event.set()
        print("\nCerrando...")


if __name__ == "__main__":
    main()
