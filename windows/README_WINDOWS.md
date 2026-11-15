# Companion de Windows — setup (anclaje USB)

Modo recomendado: teléfono conectado por **cable USB** a la PC con **anclaje USB**
activado. Menos latencia que WiFi y el teléfono queda cargándose.

## 1. Instalar dependencias
```powershell
py -m pip install -r requirements.txt
```

## 2. Instalar VB-CABLE (para el micrófono virtual)
- Descargá VB-CABLE: https://vb-audio.com/Cable/ (gratis, donationware)
- Descomprimí y ejecutá `VBCABLE_Setup_x64.exe` como **administrador**. Reiniciá.
- Crea dos dispositivos: **CABLE Input** (salida) y **CABLE Output** (entrada).

El script inyecta el micro de los auriculares en *CABLE Input*. En Zoom/Meet/Discord
elegí **CABLE Output** como micrófono y listo: te escuchan por los auriculares.

> El audio que sale de la PC se captura con **WASAPI loopback**, así que NO tenés que
> cambiar tu dispositivo de salida por defecto para esa dirección.

## 3. Activar el anclaje USB
1. Conectá el Moto E13 por USB a la PC.
2. En el teléfono: **Ajustes → Conexiones/Redes → Anclaje y zona WiFi → Anclaje USB** (ON).
3. Windows crea un adaptador nuevo ("Remote NDIS" / "Ethernet USB"). El teléfono queda
   como gateway en **192.168.42.129** (esa es la IP que usa el script por defecto).

> No hace falta que sepas la IP de la PC: la app Android la **detecta sola** a partir
> del audio que le llega. Podés dejar vacío el campo "IP de la PC" en la app.

## 4. Correr
En la app Android: emparejá los auriculares y tocá **"Activar puente de audio"**.
En la PC:
```powershell
py audio_relay_pc.py
```
(Si tu teléfono usa otra IP de gateway, pasá `--phone-ip <ip>`.)

## 5. Firewall
La primera vez Windows pregunta por el firewall: permití acceso en **redes privadas**.

## Problemas típicos
- **No se escucha nada**: verificá que los auriculares estén conectados por A2DP al
  teléfono (reproducí algo en el teléfono para confirmar).
- **El script dice que no encontró VB-CABLE**: instalalo y reiniciá; sin él no hay micro
  virtual (el audio de salida igual funciona).
- **Micro no aparece en la app de videollamada**: elegí "CABLE Output" como micrófono.
- **Delay o cortes**: el tramo teléfono↔auriculares sigue siendo Bluetooth, así que algo
  de latencia es inevitable; el USB ya minimiza el resto.
- **El adaptador USB no aparece**: reconectá el cable y re-activá el anclaje USB; algunos
  cables son solo de carga, probá otro.
