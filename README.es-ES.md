![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)
[![](https://jitpack.io/v/alikemalocalan/greentunnel4jvm.svg)](https://jitpack.io/#alikemalocalan/greentunnel4jvm)

# GreenTunnel4JVM

Green Tunnel es una utilidad contra la censura diseñada para sortear los sistemas DPI implementados por diversos proveedores de Internet (ISP) para bloquear el acceso a determinados sitios web.

Más información : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

## Características

### Anti-Censura Principal
- **DNS sobre HTTPS (DoH)** — Consultas DNS cifradas a través de Google DoH para evadir la censura basada en DNS
- **Fragmentación de TLS Client Hello** — Divide el campo SNI en fragmentos pequeños para evadir la inspección DPI
- **Mezcla de mayúsculas/minúsculas en el encabezado Host** — Aleatoriza las letras del encabezado Host (`test.com` → `tEsT.cOm`) para confundir al DPI
- **Redirección HTTP → HTTPS** — Redirige automáticamente las solicitudes HTTP en texto plano a HTTPS
- **Respuesta 200 anticipada** — Previene la filtración de dominios del lado del cliente respondiendo antes del análisis CONNECT
- **Configuración automática del proxy del sistema** — Configura automáticamente el proxy del sistema en macOS y Linux
- **Interfaz gráfica Swing** — Aplicación de escritorio con panel de registros en tiempo real

### Mejoras de Privacidad
- **Aleatorización de huella TLS** — Tamaños de fragmento aleatorios (40–160 bytes) por cada parte en lugar de un MTU fijo, imitando la segmentación TCP natural
- **Rotación realista de User-Agent** — Selecciona de un conjunto de 7 cadenas User-Agent reales (Chrome, Firefox, Safari, Edge en Windows, macOS, Linux)
- **Eliminación exhaustiva de encabezados de proxy** — Elimina 9 encabezados que revelan el uso de proxy: `Client-IP`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Real-IP`, `Forwarded`, `Via`, `Proxy-Authorization`, `Proxy-Connection`
- **Eliminación del encabezado Server** — Elimina el encabezado `Server: greenTunnel` para evitar la identificación del proxy
- **Endurecimiento de caché DNS** — Caché DNS limitada (máximo 500 entradas, 300 caché negativa) para evitar registros obsoletos y limitar el uso de memoria

## Uso

Agrega lo siguiente a tu archivo root build.gradle al final de repositories:

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Agrega la dependencia:

```groovy
dependencies {
    implementation 'com.github.alikemalocalan:greentunnel4jvm:2.7.9-SNAPSHOT'
}
```

## Captura de pantalla de la GUI
![screenshot](https://raw.githubusercontent.com/alikemalocalan/greentunnel4jvm/master/Screen-gui.png)

## Nota
La configuración del proxy del sistema se establecerá automáticamente solo para Mac y Linux. 
Aún no se ha implementado para Windows, debes configurarlo manualmente en la configuración de red de tu sistema o de Firefox.

## Descargar
[greentunnel4jvm.jar](https://github.com/alikemalocalan/greentunnel4jvm/releases/download/2.7.9-SNAPSHOT/greentunnel4jvm.jar)

## Gracias por la motivación a [0x01h](https://github.com/0x01h)

## Licencia
Licenciado bajo la licencia MIT. Consulta [LICENSE](https://github.com/alikemalocalan/green-tunnel-scala/blob/master/LICENSE "LICENSE").

