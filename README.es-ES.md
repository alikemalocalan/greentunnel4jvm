

![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)
[![](https://jitpack.io/v/alikemalocalan/greentunnel4jvm.svg)](https://jitpack.io/#alikemalocalan/greentunnel4jvm)


Green Tunnel es una utilidad contra la censura diseñada para sortear los sistemas DPI implementados por diversos proveedores de Internet (ISP) para bloquear el acceso a determinados sitios web.


Más información : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

Para usarlo :

Agrega lo siguiente a tu archivo root build.gradle al final de repositories:


```
	allprojects {
		repositories {
			maven { url 'https://jitpack.io' }
		}
	}
```

Agrega la dependencia:


```
dependencies {
	        implementation 'com.github.alikemalocalan:greentunnel4jvm:2.7.9-SNAPSHOT'
        }
```

## Captura de pantalla de la GUI
![screenshot](https://raw.githubusercontent.com/alikemalocalan/greentunnel4jvm/master/Screen-gui.png)

## Nota
La configuración del proxy del sistema se establecerá automáticamente solo para Mac y Linux. 
Aún no se ha implementado para Windows, debes configurarlo manualmente en la configuración de red de tu sistema o de Firefox.

## Descargar aplicación de escritorio ejecutable
[greentunnel4jvm.jar](https://github.com/alikemalocalan/greentunnel4jvm/releases/download/2.7.9-SNAPSHOT/greentunnel4jvm.jar)

## Gracias por la motivación a [0x01h](https://github.com/0x01h)

## Licencia
Licenciado bajo la licencia MIT. Consulta [LICENSE](https://github.com/alikemalocalan/green-tunnel-scala/blob/master/LICENSE "LICENSE").
