![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)
[![](https://jitpack.io/v/alikemalocalan/greentunnel4jvm.svg)](https://jitpack.io/#alikemalocalan/greentunnel4jvm)


Green Tunnel is an anti-censorship utility designed to bypass DPI system that are put in place by various ISPs to block access to certain websites.


About More : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

For use :

Add it in your root build.gradle at the end of repositories:


```
	allprojects {
		repositories {
			maven { url 'https://jitpack.io' }
		}
	}
```

Add the dependency:


```
dependencies {
	        implementation 'com.github.alikemalocalan:greentunnel4jvm:2.7.4-SNAPSHOT'
        }
```

## GUI ScreenShot
![screenshot](https://raw.githubusercontent.com/alikemalocalan/greentunnel4jvm/master/Screen-gui.png)

## Note
System proxy setting will be set automatically for only Mac and linux. 
It didn't implement for windows yet, you must set it manually for your system or firefox network setting.

## Download desktop executable app
[greentunnel4jvm.jar](https://github.com/alikemalocalan/greentunnel4jvm/releases/download/2.7.4-SNAPSHOT/greentunnel4jvm.jar)

## Thanks for motivation [0x01h](https://github.com/0x01h)

## License
Licensed under the MIT license. See [LICENSE](https://github.com/alikemalocalan/green-tunnel-scala/blob/master/LICENSE "LICENSE").
