# Sin Shorts

App Android de código abierto que bloquea los vídeos cortos sin bloquear las apps:

| App | Qué se bloquea | Qué sigue funcionando |
|---|---|---|
| YouTube | Pestaña Shorts y cualquier Short que abras | Vídeos normales, suscripciones, búsqueda |
| Instagram | Pestaña Reels y el visor de Reels | Feed, historias, mensajes, perfiles |
| Instagram: solo seguidos | Tapa el feed de inicio (sugerencias sin fin) con un botón que abre el feed «Siguiendo» | Feed «Siguiendo», historias, mensajes |
| Instagram: Explorar | Tapa la cuadrícula de Explorar | Buscar personas |
| Facebook *(experimental)* | Pestaña Reels | Todo lo demás |
| Snapchat *(experimental)* | Spotlight | Chats, cámara, historias |
| Navegadores | youtube.com/shorts, Reels y TikTok web | El resto de la web |
| TikTok *(desactivado por defecto)* | La app entera | — |

Cuando entras en una de esas secciones, la app pulsa «Atrás» por ti. Si la app insiste, te manda a la pantalla de inicio.

**Privacidad:** no pide permiso de Internet, así que no puede enviar nada fuera del teléfono. Solo lee la estructura de la pantalla de las apps de la lista para reconocer esas secciones.

## Instalar

1. Play Protect bloquea las apps de accesibilidad descargadas desde el navegador o un gestor de archivos. Para instalarla: Play Store → tu foto → Play Protect → ⚙️ → desactiva «Analizar apps con Play Protect», instala `SinShorts.apk` y **vuelve a activarlo**. Otra opción es `adb install SinShorts.apk` desde un ordenador.
2. Abre **Sin Shorts**. La primera vez se abre una **guía paso a paso** que:
   - te lleva directo al interruptor de accesibilidad y detecta cuándo lo has activado;
   - si Android muestra «Se ha denegado el acceso a la aplicación» o «Ajuste restringido», te explica cómo desbloquearlo y te abre la pantalla correcta;
   - te pide (opcional) que Android no la detenga para ahorrar batería;
   - te deja elegir qué bloquear;
   - abre un Short de prueba y comprueba que te saca de él.

Puedes repetir la guía desde **Opciones → Repetir la configuración guiada**. Si el servicio se desactiva, la pantalla principal te lo avisa con un botón para arreglarlo.

## Si una app se actualiza y deja de bloquearse

YouTube, Instagram, etc. cambian a veces sus IDs internos. Para arreglarlo sin tocar código:

1. En la app, activa **Modo diagnóstico**.
2. Abre la sección que quieres bloquear (por ejemplo, la pestaña Reels) y vuelve a Sin Shorts.
3. Busca el ID nuevo (lo marcado como «Seleccionado» suele ser la pestaña activa) y añade una regla en **Reglas personalizadas**.

Formato de regla, una por línea:

```
paquete[,paquete2] | tipo:valor
```

| Tipo | Significado |
|---|---|
| `id:NOMBRE` | hay una vista visible con ese ID |
| `idsel:NOMBRE` | esa vista está seleccionada (pestaña activa) |
| `text:TEXTO` | una vista visible contiene ese texto o descripción |
| `textsel:TEXTO` | igual, pero seleccionada |
| `url:TEXTO` | la barra de direcciones del navegador contiene TEXTO |
| `app` | bloquea la app entera |

Ejemplo: `com.google.android.youtube | id:reel_recycler`

Las reglas incluidas están en `app/src/main/java/io/github/sinshorts/Rules.java`.

## Compilar

**Opción A — sin Android Studio (Linux, probado):**

```bash
sudo apt install openjdk-17-jdk aapt apksigner dalvik-exchange zipalign curl
./build.sh            # → build/SinShorts.apk
```

La primera vez crea una clave de firma `sinshorts.keystore`. Guárdala: para instalar una versión nueva encima de la anterior hay que firmarla con la misma clave.

**Opción B — GitHub:** sube el proyecto a un repositorio; el flujo `.github/workflows/build.yml` compila el APK en cada cambio y lo deja en la pestaña *Actions*.

**Opción C — Android Studio:** abre la carpeta del proyecto (incluye `build.gradle`).

## Estructura

```
app/src/main/
  AndroidManifest.xml
  java/io/github/sinshorts/
    BlockerService.java   servicio de accesibilidad: detecta y sale
    Rules.java            reglas incluidas y formato de reglas
    MainActivity.java     pantalla de ajustes
    Diagnostics.java      capturas del modo diagnóstico
  res/xml/accessibility_service.xml
```

Licencia MIT.
