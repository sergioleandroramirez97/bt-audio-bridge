# BT Audio Bridge — puente de audio Bluetooth entre Android y Windows

Reutiliza un Android viejo (p. ej. Moto E13) para usar tus **auriculares Bluetooth
con micrófono** en una PC con Windows, cuando la PC no tiene Bluetooth o lo tiene malo.

> ⚠️ **Antes de construir nada, leé "La realidad técnica".** El objetivo "ideal" que
> describiste (que Windows se empareje *a través* del teléfono por Bluetooth) **no es
> posible** con una app normal. Lo que SÍ se puede hacer es un puente por WiFi. Acá te
> explico por qué y qué construí.

---

## La realidad técnica (importante)

Tu idea tal cual: *"el teléfono detecta los auriculares, y Windows se empareja al
teléfono o a través del teléfono por Bluetooth"*.

El problema es el **rol A2DP Sink**. Para que Windows le "mande" el audio al teléfono
por Bluetooth, el teléfono tendría que comportarse como un **parlante/manos libres
Bluetooth** (rol A2DP *sink* + HFP). Android **no expone ese rol a las apps**: no hay
API pública para que una app reciba audio por Bluetooth desde otro equipo. Encima, el
teléfono tendría que ser sink (desde Windows) **y** source (hacia los auriculares) al
mismo tiempo, con doble codificación y latencia inservible para un micrófono.

Conclusión honesta, en orden de practicidad:

1. **Si el problema real es que la PC no tiene Bluetooth** → un **dongle USB Bluetooth**
   (USD 3–8) lo resuelve en 10 minutos y con mejor latencia y calidad que cualquier
   puente. Es, de lejos, la mejor opción. No te la oculto para venderte el proyecto.

2. **Si querés reutilizar el Moto E13 igual** (sin comprar nada, o porque el dongle no
   alcanza) → **puente de audio por WiFi**, que es lo que construí acá:
   - El teléfono se conecta a los auriculares por Bluetooth (A2DP + SCO para el micro).
   - El teléfono y la PC se hablan por **la red WiFi** (sockets), NO por Bluetooth.
   - El audio del sistema de Windows viaja PC → teléfono → auriculares.
   - El micrófono viaja auriculares → teléfono → PC.

3. **El "relay transparente por Bluetooth"** (Windows cree que habla con los auriculares
   pero pasa por el teléfono) → **no se puede** con app a nivel usuario. Requeriría ROM
   custom / modificar el stack Bluetooth del teléfono. Fuera de alcance.

### El punto débil del lado Windows
Para que el **micrófono** de los auriculares aparezca como un micrófono real en Zoom,
Meet, Discord, etc., Windows necesita un **dispositivo de audio virtual**. Una app de
usuario común **no puede crear una placa de sonido virtual** sin un driver firmado. La
solución práctica y gratuita es **VB-CABLE** (VB-Audio). El companion de Windows que te
dejé lo usa. Sin VB-CABLE podés escuchar el audio, pero no inyectar el micro en otras apps.

### Latencia (expectativa realista)
Red WiFi + buffers + Bluetooth SCO = vas a notar un retardo. Para **escuchar música o
videos** anda bien. Para **llamadas/juegos** el micro puede sentirse con delay y la
calidad del micro Bluetooth (SCO) es baja de por sí. Es una limitación física del
camino, no del código.

---

## Qué incluye este proyecto

```
bt-audio-bridge/
├── README.md               ← este archivo
├── android/                ← app Android (Kotlin + Jetpack Compose)
│   └── app/src/main/...     scan/emparejado BT + servicio de relay por WiFi
└── windows/                ← companion de PC (Python + sounddevice)
    ├── audio_relay_pc.py
    ├── requirements.txt
    └── README_WINDOWS.md    ← incluye setup de VB-CABLE
```

**Estado:** es un **esqueleto funcional / punto de partida**, no un release probado.
No pude compilarlo ni probarlo en un dispositivo real desde acá. La parte de
escaneo/emparejamiento Bluetooth está completa; la de audio tiene la lógica real de
sockets + AudioTrack/AudioRecord pero seguro necesita ajuste de buffers y pruebas en el
Moto E13 (Android 13 Go, chip Unisoc — gama baja, ojo con el rendimiento).

---

## Decisiones tomadas

- **Transporte: USB (anclaje USB).** El teléfono va siempre conectado por cable: menos
  latencia que WiFi y queda cargándose. Los mismos sockets UDP viajan por la red del USB.
- **Micrófono virtual: VB-CABLE** en Windows (gratis). Confirmado.
- **IP automática:** la app detecta sola la IP de la PC desde el audio entrante; el campo
  "IP de la PC" quedó opcional. El script usa `192.168.42.129` (gateway del anclaje USB)
  por defecto.

Setup paso a paso en `windows/README_WINDOWS.md`.

---

## Cómo obtener el APK

### Opción A — Link directo con GitHub Actions (recomendado, sin instalar nada)
El proyecto ya trae un workflow (`.github/workflows/build-apk.yml`) que compila el APK
en la nube y lo publica como **Release**. Pasos:

1. Creá un repo en GitHub (público o privado) y subí el contenido de esta carpeta.
2. GitHub Actions compila solo en cada push. Entrá a la pestaña **Actions** para ver el build.
3. Cuando termina, el APK queda en **Releases → latest**. El link directo para el teléfono es:
   ```
   https://github.com/<tu-usuario>/<tu-repo>/releases/latest/download/app-debug.apk
   ```
4. Abrí ese link en el navegador del Moto E13, descargá e instalá (hay que permitir
   "instalar apps de orígenes desconocidos"). Es un APK **de debug**, sin firmar para
   Play Store, pero instalable por sidELoad sin problema.

### Opción B — Android Studio (local)
1. Abrí la carpeta `android/` en **Android Studio** (Hedgehog o más nuevo, JDK 17).
2. Dejá que sincronice Gradle.
3. Conectá el Moto E13 por USB con depuración activada y dale **Run**.

Después, en Windows: seguí `windows/README_WINDOWS.md`.
