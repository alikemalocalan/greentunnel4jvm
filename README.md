![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)

Green Tunnel is an anti-censorship utility designed to bypass DPI system that are put in place by various ISPs to block access to certain websites.


About More : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

For use :

```xml
<dependency>
  <groupId>com.github.alikemalocalan</groupId>
  <artifactId>greentunnel4jvm</artifactId>
  <version>1.2-snapshot</version>
</dependency>
```

and then:

```bash
mvn install
```

code:

```kotlin
HttpProxyServer.newProxyService().start()

```


Thanks for motivation [0x01h](https://github.com/0x01h)


## License
Licensed under the MIT license. See [LICENSE](https://github.com/alikemalocalan/green-tunnel-scala/blob/master/LICENSE "LICENSE").
