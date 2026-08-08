![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)
[![](https://jitpack.io/v/alikemalocalan/greentunnel4jvm.svg)](https://jitpack.io/#alikemalocalan/greentunnel4jvm)

# GreenTunnel4JVM

Green Tunnel is an anti-censorship utility designed to bypass DPI system that are put in place by various ISPs to block access to certain websites.

About More : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

## Features

### Core Anti-Censorship
- **DNS over HTTPS (DoH)** — DNS queries encrypted via Google DoH to bypass DNS-based censorship
- **TLS Client Hello Fragmentation** — Splits SNI field into small chunks to evade DPI inspection
- **SNI-Targeted Fragmentation & Timing Delay** — Locates SNI in ClientHello binary structure and splits hostname at midpoint with random 1-30ms timing delay to evade DPI reassembly (new feature for Russian users but not tested)
- **TLS Record Layer Fragmentation** — Splits ClientHello across multiple Layer 5 TLS Records in addition to Layer 4 TCP segmentation (new feature for Russian users but not tested)
- **Host Header Case-Mixing** — Randomizes letter case in Host header (`test.com` → `tEsT.cOm`) to confuse DPI
- **HTTP → HTTPS Redirect** — Automatically redirects plaintext HTTP requests to HTTPS
- **Premature 200 Response** — Prevents client-side domain leaking by responding before CONNECT parsing
- **System Proxy Auto-Configuration** — Automatically sets system proxy on macOS and Linux
- **Swing GUI** — Desktop GUI with real-time log panel

### Privacy Improvements
- **TLS Fingerprint Randomization** — Random fragment sizes (40–160 bytes) per chunk instead of fixed MTU, mimicking natural TCP segmentation
- **Realistic User-Agent Rotation** — Picks from a pool of 7 real-world User-Agent strings (Chrome, Firefox, Safari, Edge across Windows, macOS, Linux)
- **Comprehensive Proxy Header Stripping** — Removes 9 proxy-revealing headers: `Client-IP`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Real-IP`, `Forwarded`, `Via`, `Proxy-Authorization`, `Proxy-Connection`
- **Server Header Removal** — Eliminates `Server: greenTunnel` header to prevent proxy fingerprinting
- **DNS Cache Hardening** — Bounded DNS cache (max 500 entries, 300 negative cache) to prevent stale records and limit memory usage

## Usage

Add it in your root build.gradle at the end of repositories:

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```groovy
dependencies {
    implementation 'com.github.alikemalocalan:greentunnel4jvm:2.8.0'
}
```

## GUI Screenshot
![screenshot](https://raw.githubusercontent.com/alikemalocalan/greentunnel4jvm/master/Screen-gui.png)

## Note
System proxy setting will be set automatically for only Mac and Linux. 
It didn't implement for Windows yet, you must set it manually for your system or Firefox network setting.

## Download
[greentunnel4jvm.jar](https://github.com/alikemalocalan/greentunnel4jvm/releases/download/2.8.0/greentunnel4jvm.jar)

## Thanks for motivation [0x01h](https://github.com/0x01h)

## License
Licensed under the MIT license. See [LICENSE](https://github.com/alikemalocalan/green-tunnel-scala/blob/master/LICENSE "LICENSE").

