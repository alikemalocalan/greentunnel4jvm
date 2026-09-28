![Build Gradle](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Build%20Gradle/badge.svg?branch=master)
![Publish release](https://github.com/alikemalocalan/greentunnel4jvm/workflows/Publish%20release/badge.svg?branch=master&event=release)
[![](https://jitpack.io/v/alikemalocalan/greentunnel4jvm.svg)](https://jitpack.io/#alikemalocalan/greentunnel4jvm)

# GreenTunnel4JVM

Green Tunnel is an anti-censorship utility designed to bypass DPI system that are put in place by various ISPs to block access to certain websites.

About More : [GreenTunnel](https://github.com/SadeghHayeri/GreenTunnel)

## Features

### Core Anti-Censorship
- **DNS over HTTPS (DoH)** — DNS queries encrypted via Google DoH to bypass DNS-based censorship (with GUI toggle)
- **TLS Client Hello Fragmentation** — Splits SNI field into small chunks to evade DPI inspection
- **SNI-Targeted Fragmentation & Timing Delay (Faz 1)** — Locates SNI in ClientHello binary structure and splits hostname at midpoint with random 1-30ms timing delay to evade DPI reassembly
- **TLS Record Layer Fragmentation (Faz 2)** — Splits ClientHello across multiple Layer 5 TLS Records in addition to Layer 4 TCP segmentation
- **QUIC / HTTP/3 Alt-Svc Header Stripping (Faz 6)** — Strips `Alt-Svc` headers from HTTP responses to force browsers to stay on TCP TLS 1.3, evading ISP QUIC UDP port 443 SNI throttling and blackholing
- **TCP Source Port Rotation (Faz 7)** — Ephemeral local port binding (`InetSocketAddress(0)`) and auto-retry on drop to evade TSPU's 420-second (7-minute) IP:Port 4-tuple blackhole drop lists
- **Active Probing Scanner Defense & Web Server Mimicry (Faz 12)** — Serves realistic Nginx 404 HTML server banners on invalid scanner probes to mislead ISP middlebox active fingerprinting
- **Host Header Case-Mixing** — Randomizes letter case in Host header (`test.com` → `tEsT.cOm`) to confuse DPI
- **HTTP → HTTPS Redirect** — Automatically redirects plaintext HTTP requests to HTTPS
- **Premature 200 Response** — Prevents client-side domain leaking by responding before CONNECT parsing
- **System Proxy Auto-Configuration** — Automatically sets system proxy on macOS and Linux
- **Swing GUI** — Desktop GUI with real-time log panel, DoH toggle, Alt-Svc toggle, and Port Rotate toggle

### Planned Anti-Censorship Features (Roadmap / GreentunnelRS Alignment)
> The following features are identified from TSPU/DPI community research (net4people, Zapret, ByeDPI) and aligned with **greentunnelRS**. They are in the planning stage (not yet implemented in code).
>
> ⚠️ **Note on Instagram/Meta & Cloudflare:** Instagram/Meta (Facebook, WhatsApp) experiences server-side TCP connection resets caused by strict C++ Fizz TLS stacks, and Cloudflare Anycast IP prefixes (`104.21.x.x`, `172.67.x.x`) are selectively blocked at the ISP routing level in Russia. These cases are skipped for now to avoid premature complexity, with Instagram/Meta Fizz TLS noted as a distinct issue to be tackled in a future update.

- **[RS #20] Multi-Split TLS Record Fragmentation (3+ Records)** — Splits binary `ClientHello` into 3 or more independent valid TLS records (Cipher Suites boundary, SNI prefix, SNI suffix) to completely defeat TSPU's updated dual-fragment sliding-window reassembly buffers (net4people #546, #581).
- **[Faz 4 / RS #3] Meta / Instagram Ekosistemi Özel Bypass Stratejisi (Fizz TLS & Zero-Delay)** — Ayrı bir faz olarak planlanmıştır (henüz kodlanmadı). Meta'nın katı C++ Fizz TLS yığınının inter-fragment delay ve padding nedeniyle sunucu seviyesinde TCP RST fırlatmasını önlemek için sıfır gecikme (zero-delay) ve no-padding kuralı uygular.
- **[RS #22] TCP Out-Of-Band (OOB / Urgent Pointer) Desync** — Sends 1-byte TCP `URG` out-of-band dummy data (`Socket.sendUrgentData`) to desynchronize middlebox stream parsers with zero round-trip latency overhead.
- **[RS #25] Cloudflare Anycast Clean IP Pool Mapping (`--cf-clean-ip`)** — Transparently maps blocked Cloudflare Anycast IP subnets (`104.21.x.x`, `172.67.x.x`) to unblocked clean Anycast IP prefixes (`104.16.x.x`, `104.18.x.x`) while preserving the original TLS SNI (net4people #662).
- **[RS #21] In-Stream Fake TLS TTL Desync** — Injects a benign decoy ClientHello (`google.com`) with a low TTL in-stream before real records to fool DPI stream state machines.
- **[RS #23, #24] Preset Evasion Profiles & Adaptive Multi-Strategy Auto Fallback** — Pre-configured regional profiles (e.g. `russia-youtube-discord`) and automatic strategy rotation on `ECONNRESET` or handshake timeouts.
- **[RS #19, #197] FQDN Trailing Dot & HTTP CONNECT Space Insertion** — Appends trailing dot (`domain.com.`) and inserts extra spaces in CONNECT requests to defeat naive exact-string regex middlebox rules.

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

