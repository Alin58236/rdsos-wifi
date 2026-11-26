# Flow-ul complet: de la placa de retea pana in browser

Documentul asta urmareste **un frame** din momentul in care placa Wi-Fi il prinde din aer pana cand apare ca rand in UI. Pentru fiecare pas spune:
- ce date intra si ce iese;
- ce verifica parserul si ce se intampla cand verificarea pica.

La final (§12) e partea pentru intrebarile profesorului: **de unde stii cum se citesc bitii**, cu metoda, sursele si raspunsuri pregatite.

Exemplele folosesc **bytes reali** din fixtures (`backend/src/test/resources/fixtures/`):
- `beacons-wpa2.pcap`: un beacon de la `HomeNetwork`;
- `managed-http.pcap`: un `GET / HTTP/1.1` facut cu `curl http://neverssl.com`.

---

## Cuprins

0. Harta in 30 de secunde
1. Pornirea aplicatiei
2. Monitor vs managed: `InterfaceService`
3. Captura: libpcap prin FFM (`LibPcap`, `PcapLiveSource`) si replay (`PcapFileReader`)
4. Thread-ul de captura: `CaptureService`
5. `PacketDecoder`: cine decide ce parser urmeaza
6. Parserele, unul cate unul (cu bytes reali)
7. Registry-ul de AP-uri
8. Pachetele: `PacketStore` → `PacketViewMapper` → WebSocket / REST
9. Frontend-ul
10. Thread-uri si date partajate
11. Toate validarile intr-un singur tabel
12. "De unde stii cum se citesc bitii?" (metoda, surse, raspunsuri)

---

## 0. Harta in 30 de secunde

```
 aer (unde radio)
   │
   ▼
 placa Wi-Fi RTL8822CE  ── driver rtw88 (kernel) ──  monitor: adauga Radiotap, lasa 802.11 intact
   │                                                 managed: decripteaza, scoate 802.11, pune Ethernet
   ▼
 libpcap (C)  ─────────────  pcap_next_ex() da: header (timestamp, lungimi) + pointer la bytes
   │  FFM (java.lang.foreign)
   ▼
 PcapLiveSource.next()  ───  copiaza bytes-ii in byte[]  →  RawFrame(timestamp, linkType, length, data)
   │                         (la replay: PcapFileReader citeste acelasi RawFrame dintr-un fisier .pcap)
   ▼
 thread-ul "capture" (CaptureService.liveLoop / fileReplayLoop)
   │
   ▼
 CaptureService.processFrame(frame)
   │   frames++
   ├─► PacketDecoder.decode(frame)  ──►  DecodedPacket (toate headerele, cat s-a putut citi)
   │        linkType 127: RadioTap → Dot11 → [Beacon] → [criptat? stop] → LLC/SNAP → IPv4 → TCP → HTTP
   │        linkType 1:   Ethernet → IPv4 → TCP → HTTP
   │        orice IllegalArgumentException  →  parseErrors++, frame-ul e ignorat
   ├─► PacketStore.add(packet)       ──►  id crescator, ultimele 5000
   └─► daca e beacon: AccessPointRegistry.update(...)  ──►  AccessPoint (SSID, canal, securitate, vendor...)

 thread-ul scheduler, la fiecare 200 ms:  UpdatesPublisher.publish()
   │   status + registry.snapshot() + packetStore.after(lastSentId, 500) → PacketViewMapper.toSummary
   ▼
 WebSocket /ws/updates  ──►  browser: useLiveUpdates  ──►  tabel AP, grafic, lista de pachete

 click pe un rand:  GET /api/packets/{id}  ──►  PacketStore.get(id)  ──►  PacketViewMapper.toDetail
   ▼
 browser: arborele de layere + hex dump
```

**Ideea centrala a proiectului:** libpcap face doar doua lucruri, deschide interfata si ne da bytes. **Tot ce inseamna "ce e in acesti bytes" e scris de noi**, pe `byte[]`, in `service/parser/`.

---

## 1. Pornirea aplicatiei

**Backend:** `sudo java --enable-native-access=ALL-UNNAMED -jar target/wifianalyzer-0.0.1-SNAPSHOT.jar`, pornit din `backend/`.
- **De ce `sudo`:** captura pe o interfata reala si comenzile `ip`, `iw`, `nmcli`, `modprobe` cer root (sau capabilitatile `CAP_NET_RAW` + `CAP_NET_ADMIN`).
- **De ce `--enable-native-access`:** FFM e "restricted". Java avertizeaza cand un cod apeleaza C, iar flag-ul spune "stiu, e voie". (In jar e trecut si in manifest, `Enable-Native-Access: ALL-UNNAMED`.)
- **Spring creeaza o singura instanta (singleton)** din fiecare clasa `@Component` / `@Service` / `@RestController`:
  - `OuiDatabase`: citeste `oui.txt.gz` (lista IEEE de producatori) intr-un `HashMap`. Dureaza ~0.5 s, o singura data;
  - `AccessPointRegistry`, `PacketStore`, `CaptureService`, `ChannelHopper`, `InterfaceService`;
  - `UpdatesPublisher`, `UpdatesWebSocketHandler`, controller-ele, `PacketViewMapper`.
- **Securitate:**
  - asculta doar pe `127.0.0.1:8080` (`server.address`). Ruleaza ca root, deci nu trebuie sa fie accesibil din retea;
  - CORS si WebSocket accepta doar originea `http://localhost:5173`.

**Frontend:** `npm run dev` porneste Vite pe `localhost:5173`. Vite face **proxy**: tot ce e `/api/...` si `/ws/...` merge mai departe la `127.0.0.1:8080`. Pentru browser, totul vine de la acelasi server.

---

## 2. Monitor vs managed: `InterfaceService`

| | **monitor mode** | **managed mode** |
|---|---|---|
| ce face placa | asculta tot ce e pe canal, nu e conectata la nicio retea | e conectata la un AP, ca un laptop normal |
| ce primim | frame-uri 802.11 brute + header Radiotap | doar traficul nostru, **deja decriptat**, ca Ethernet |
| link type (libpcap) | **127** `DLT_IEEE802_11_RADIO` | **1** `DLT_EN10MB` |
| la ce foloseste | Project 1 (beacons) + sniffer pe orice retea deschisa | sniffer pe traficul laptopului |

**`switchToMonitor(name)`** ruleaza, in ordine:
1. `modprobe -r rtw88_8822ce` + `modprobe rtw88_8822ce` (**reload driver**), apoi asteapta pana reapare interfata in `/sys/class/net`. Fara reload, radio-ul ramane blocat pe banda de 5 GHz a conexiunii de la boot si nu prinde nimic pe 2.4 GHz (testat, `HANDOFF.md` §4);
2. `nmcli dev set wlp4s0 managed no`: NetworkManager nu mai are voie sa se atinga de interfata (altfel o pune inapoi in managed);
3. `ip link set wlp4s0 down`, apoi `iw dev wlp4s0 set type monitor`, apoi `ip link set wlp4s0 up`.

**`switchToManaged(name)`** face invers: `down` → `set type managed` → `up` → `nmcli ... managed yes`.

**Modul curent** se citeste din `/sys/class/net/wlp4s0/type`: `1` = managed (Ethernet), `803` = monitor (Radiotap).

**Securitate:** numele interfetei vine din API, deci de la utilizator. Inainte sa ajunga intr-o comanda:
- trebuie sa respecte `[A-Za-z0-9_.-]{1,15}`;
- trebuie sa existe `/sys/class/net/<nume>/phy80211` (e o interfata wireless reala).

Comenzile se ruleaza cu `ProcessBuilder` si **argumente separate**, niciodata ca un string dat unui shell. Asa nu se poate injecta `; rm -rf /`.

**La oprirea backend-ului** (`@PreDestroy restoreManagedMode`), fiecare interfata pusa de noi in monitor e pusa inapoi in managed. Laptopul nu ramane fara internet.

**Channel hopper** (`ChannelHopper`, thread-ul "channel-hopper"):
- in monitor mode ruleaza la fiecare 250 ms `iw dev wlp4s0 set channel N`, cu N = 1, 2, …, 13, 1, …;
- **de ce:** placa asculta un singur canal odata. Ca sa vedem AP-urile de pe toate cele 13 canale, trebuie sa le plimbam;
- **pin:** cand fixezi un canal, hopper-ul se opreste pe el. Pentru sniffing pe o retea anume e obligatoriu, altfel prinzi doar ~1/13 din trafic.

---

## 3. Captura: libpcap prin FFM

### 3.1 Ce e FFM (Foreign Function & Memory API, `java.lang.foreign`, JEP 454)

Mod de a apela functii C din Java **fara JNI si fara cod C scris de noi**. Piesele, toate in `LibPcap.java`:

| piesa | ce face | la noi |
|---|---|---|
| `SymbolLookup.libraryLookup("libpcap.so.0.8", Arena.global())` | incarca biblioteca si cauta functii dupa nume | o data, static |
| `FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)` | descrie semnatura C: tip intors + tipuri parametri. `ADDRESS` = pointer, `JAVA_INT` = `int` | cate unul per functie |
| `Linker.nativeLinker().downcallHandle(adresa, descriptor)` | face din functia C un `MethodHandle` Java | 11 handle-uri |
| `handle.invokeExact(...)` | apeleaza functia C | in metodele `create`, `nextEx` etc. |
| `Arena.ofShared()` | o zona de memorie nativa (in afara heap-ului Java), eliberata toata la `close()`. "Shared" pentru ca e deschisa pe un thread HTTP si folosita pe thread-ul de captura | una per captura |
| `MemorySegment` | o bucata de memorie nativa: pointer + lungime | pointerii si bufferele |

De ce `libpcap.so.0.8`: Ubuntu livreaza libpcap 1.x sub numele vechi; nu exista `libpcap.so.1`.

### 3.2 Deschiderea interfetei: `PcapLiveSource.openInterface("wlp4s0")`

| apel C | parametru | de ce |
|---|---|---|
| `pcap_create("wlp4s0", errbuf)` | | creeaza handle-ul `pcap_t*`, inca inactiv. La eroare intoarce `NULL` si scrie mesajul in `errbuf` (256 bytes) |
| `pcap_set_snaplen(h, 65535)` | 65535 | pastreaza frame-ul intreg, nu doar primii N bytes |
| `pcap_set_promisc(h, 1)` | 1 | primeste si frame-uri care nu sunt pentru noi |
| `pcap_set_timeout(h, 100)` | 100 ms | `pcap_next_ex` se intoarce cel tarziu dupa 100 ms, chiar fara trafic, ca bucla sa poata verifica `running` |
| `pcap_set_immediate_mode(h, 1)` | 1 | da frame-urile imediat, nu le strange in buffer |
| `pcap_activate(h)` | | porneste captura. `< 0` = eroare → `pcap_geterr` → `IllegalStateException` → HTTP 409 in UI |
| `pcap_datalink(h)` | | intoarce **link type**-ul (127 sau 1), salvat o data si pus in fiecare `RawFrame` |

**Nu chemam `pcap_set_rfmon`.** Monitor mode il seteaza `InterfaceService` cu `iw`. `rfmon` poate crea o interfata separata `mon0` si s-ar bate cap in cap cu hopper-ul.

### 3.3 Citirea unui frame: `PcapLiveSource.next()`

Semnatura C: `int pcap_next_ex(pcap_t *p, struct pcap_pkthdr **pkt_header, const u_char **pkt_data)`.

`**` inseamna ca functia **scrie doi pointeri** in locurile pe care i le dam. De aceea alocam doua "cutii" de marimea unui pointer, `headerPointerBox` si `dataPointerBox`. libpcap pune in ele adresele unde sunt headerul si bytes-ii.

```
rezultat = pcap_next_ex(handle, headerPointerBox, dataPointerBox)

 1  → avem frame
 0  → timeout de 100 ms fara trafic           → intoarcem null (nu e o eroare)
-2  → sfarsit de fisier (doar la offline)     → null
-1  → eroare                                  → IllegalStateException
```

Cand avem frame:
1. `headerPointerBox.get(ADDRESS, 0)` = pointerul la `struct pcap_pkthdr`. Un pointer venit din C are lungime 0 pentru Java, deci `.reinterpret(24)` ii spune lui Java "ai voie sa citesti 24 de bytes de acolo";
2. **`struct pcap_pkthdr` pe Linux 64-bit** (24 bytes, ordinea masinii = little-endian, citit cu `JAVA_LONG`/`JAVA_INT`):

   | offset | marime | camp | ce e |
   |---|---|---|---|
   | 0 | 8 | `tv_sec` | secunde de la 1970 |
   | 8 | 8 | `tv_usec` | microsecunde |
   | 16 | 4 | `caplen` | cati bytes avem efectiv (≤ snaplen) |
   | 20 | 4 | `len` | cat de mare era frame-ul original |

3. `dataPointerBox.get(ADDRESS, 0).reinterpret(caplen).toArray(JAVA_BYTE)` = **copia** bytes-ilor intr-un `byte[]` Java. Copia e obligatorie: libpcap refoloseste acelasi buffer la urmatorul apel;
4. `new RawFrame(sec * 1_000_000 + usec, linkType, len, data)`.

**`RawFrame`** (`@Value`, imutabil, sigur intre thread-uri): `timestampMicros`, `linkType`, `originalLength`, `byte[] data`. Din momentul asta nu mai exista nimic "C": doar un `byte[]`.

### 3.4 Inchiderea

`close()`: intai `pcap_close(handle)` (libpcap isi elibereaza resursele), apoi `arena.close()` (eliberam memoria noastra nativa: cutiile, numele, `errbuf`). E idempotent, deci un al doilea apel nu face nimic.

### 3.5 Replay din fisier: `PcapFileReader` (scris de mana, fara libpcap)

Formatul `.pcap` clasic (`man pcap-savefile`) e: **un header global de 24 bytes**, apoi pentru fiecare frame **un header de 16 bytes + bytes-ii frame-ului**.

Headerul global real din `managed-http.pcap`:
```
d4 c3 b2 a1 | 02 00 | 04 00 | 00 00 00 00 | 00 00 00 00 | 00 00 04 00 | 01 00 00 00
magic       | major | minor | thiszone    | sigfigs     | snaplen     | linktype = 1
```

- **magic number:** `a1b2c3d4` citit little-endian.
  - **Daca iese `a1b2c3d4`**, fisierul e scris in ordinea noastra. Daca iese inversat (`d4c3b2a1`), toate campurile sunt big-endian.
  - **`a1b23c4d`** = timestamps in **nanosecunde**, nu microsecunde, deci impartim la 1000.
  - **`0a0d0d0a`** = format **pcapng** (alt format). Il refuzam explicit.
  - Altceva = `IllegalArgumentException`.
- **linktype** la offset 20: aici `1` = Ethernet (capturat in managed).
- **header de frame**, 16 bytes: `ts_sec` (4), `ts_frac` (4), `incl_len` (4, cati bytes urmeaza), `orig_len` (4).
- **Validari:**
  - fisier < 24 bytes → nu e pcap;
  - mai putin de 16 bytes pentru headerul de frame → trunchiat;
  - `start + incl_len > lungimea fisierului` → frame trunchiat.

Rezultatul e **acelasi `RawFrame`**. De aceea tot ce urmeaza nu stie daca frame-ul vine live sau din fisier.

---

## 4. Thread-ul de captura: `CaptureService`

**`startLive("wlp4s0")`** (`synchronized`):
1. daca `running` e deja `true` → `IllegalStateException` → HTTP 409 "A capture is already running";
2. citeste modul interfetei (monitor/managed) din sysfs;
3. `PcapLiveSource.openInterface(...)` (§3.2);
4. daca e monitor → `channelHopper.start(...)`;
5. seteaza `sourceType = "LIVE"`, `sourceName`, `mode`, `running = true`;
6. porneste thread-ul **"capture"** (platform thread, daemon) cu `liveLoop(source)`.

**`liveLoop`:**
```java
while (running) {
    RawFrame frame = source.next();   // blocheaza max 100 ms
    if (frame != null) processFrame(frame);
}
// la orice RuntimeException: log warn, running = false; finally: source.close()
```

**`startReplay(file)`** e la fel, dar cu `fileReplayLoop`:
- deschide fisierul;
- ia frame-urile pe rand, cu `Thread.sleep(100)` intre ele, ca in UI sa se vada "live";
- la final o ia de la capat. Un fisier fara niciun frame opreste replay-ul.

**`processFrame(frame)`**, inima backend-ului:
```java
frames.incrementAndGet();
try {
    DecodedPacket packet = packetDecoder.decode(frame);
    packetStore.add(packet);
    if (packet.getBeacon() != null) {
        registry.update(frame.getData(), packet.getRadioTap(), packet.getDot11(), packet.getBeacon(), System.currentTimeMillis());
    }
} catch (IllegalArgumentException e) {
    parseErrors.incrementAndGet();
}
```
**Regula pentru erori:** orice parser care gaseste date invalide arunca `IllegalArgumentException`. Aici e prinsa: frame-ul nu intra nicaieri, contorul `parseErrors` creste, iar captura **merge mai departe**. Un frame stricat nu opreste aplicatia.

**`stop()`** (`synchronized`, si `@PreDestroy`):
1. `running = false`;
2. `captureThread.join()`, adica asteapta ca bucla sa iasa (max ~100 ms, datorita timeout-ului);
3. `channelHopper.stop()`;
4. sterge campurile de status.

**De ce `volatile` / `synchronized` / `AtomicLong`:**
- **`volatile boolean running`:** il scrie thread-ul HTTP (`stop()`) si il citeste thread-ul de captura (`while (running)`). Fara `volatile`, thread-ul de captura ar putea sa nu vada niciodata schimbarea, pentru ca o tine intr-un cache;
- **`synchronized` pe `startLive` / `startReplay` / `stop`:** "verifica `running`, apoi porneste" trebuie sa fie atomic. Doua click-uri simultane pe Start ar putea altfel porni doua thread-uri;
- **`AtomicLong frames`, `parseErrors`:** le incrementeaza thread-ul de captura si le citeste scheduler-ul. `incrementAndGet` e atomic, fara lock.

---

## 5. `PacketDecoder`: cine decide ce parser urmeaza

`decode(RawFrame)` → `DecodedPacket` (`@Value @Builder`). Construieste pachetul **layer cu layer**, cu o variabila `offset` care avanseaza. **Se opreste devreme** (`return packet.build()`) cand nu mai e nimic de decodat. Layerele necitite raman `null`.

```
linkType == 1 (managed)
    Ethernet @0                       etherType, offset = 14 (sau 18 cu VLAN)

linkType == 127 (monitor)
    RadioTap @0                       offset = radioTap.length
    Dot11 @offset
    ├─ type 0 si subtype 8 sau 5 (beacon / probe response) → Beacon @(radioTap + dot11), pana la final minus FCS
    ├─ type != 2 (nu e data)                → STOP (management, control: ACK, RTS, CTS, ...)
    ├─ bitul Protected                      → encrypted = true, STOP
    ├─ subtype & 0x04 (Null / QoS Null)     → STOP (frame de date fara date)
    └─ LLC/SNAP @(radioTap + dot11)         etherType, offset += 8

altceva                                     → IllegalArgumentException("Unsupported link type")

comun:
    etherType != 0x0800                     → STOP (IPv6, ARP, EAPOL... nu sunt in scope)
    IPv4 @offset                            offset += ipv4.headerLength
    protocol != 6                           → STOP (UDP, ICMP... nu sunt in scope)
    TCP @offset                             offset += tcp.headerLength
    payloadLength = ipv4.totalLength − ipv4.headerLength − tcp.headerLength
    (sursa == 80 sau destinatia == 80) si payloadLength > 0 → HTTP @offset, payloadLength
```

**De ce `payloadLength` din IPv4 si nu "pana la capatul frame-ului":**
- in managed, Ethernet umple frame-urile mici pana la 60 de bytes cu zero-uri (padding);
- in monitor, la final pot fi 4 bytes de **FCS** (CRC-ul frame-ului).

`totalLength` din IPv4 spune exact cat e pachetul IP.

---

## 6. Parserele, unul cate unul

Toate au forma `parse(byte[] data, int offset)`: primesc **tot frame-ul** si pozitia de unde incepe layer-ul lor, si intorc un obiect imutabil (`@Value`). Nu copiaza frame-ul, doar citesc din el.

### 6.0 `ByteReader`: fundatia

In Java **`byte` e cu semn** (-128..127). Byte-ul `0xC0` (192) citit direct e `-64`. De aceea:

| metoda | ce face | de ce |
|---|---|---|
| `unsigned8(d, o)` | `d[o] & 0xFF` | `& 0xFF` pastreaza doar cei 8 biti de jos, deci rezultatul e 0..255 |
| `signed8(d, o)` | `d[o]` | pentru dBm, care **chiar** sunt negative (`0xA6` = -90) |
| `unsigned16LittleEndian` | `low | (high << 8)` | primul byte e cel **mic** (Radiotap, 802.11) |
| `unsigned16BigEndian` | `(high << 8) | low` | primul byte e cel **mare** (Ethernet, IP, TCP: "network byte order") |
| `unsigned32...` | la fel, pe 4 bytes, intors ca `long` | un `int` Java nu poate tine 0..4 294 967 295 (sequence number TCP) |
| `mac(d, o)` | 6 bytes → `"50:d4:f7:03:12:f3"` | `%02x` = hex pe 2 cifre |
| `ipv4Address(d, o)` | 4 bytes → `"192.168.0.165"` | fiecare byte `& 0xFF`, separati prin `.`; **fara `InetAddress`**, ar fi parsare de biblioteca |

**Exemplu de endianness**, bytes-ii `99 09`:
- little-endian: `0x0999` = 2457 (frecventa din Radiotap);
- big-endian: `0x9909` = 39177.

Aceiasi bytes, numere diferite. De asta fiecare protocol are scris in standard ce ordine foloseste.

**Toate metodele verifica limitele** cu `Objects.checkFromIndexSize(offset, n, length)`. Daca se citeste in afara array-ului, arunca exceptie in loc sa citeasca gunoi.

### 6.1 Radiotap (`RadioTapParser`), doar monitor

Radiotap **nu e un protocol care circula prin aer**. E un header pe care **driverul** il pune in fata frame-ului 802.11, cu informatii despre receptie: semnal, frecventa, rata. Tot ce e in el e **little-endian**.

**Structura:**
```
offset 0   version (1)      mereu 0
offset 1   pad (1)
offset 2   length (2, LE)   lungimea TOTALA a headerului Radiotap → aici incepe 802.11
offset 4   present (4, LE)  bitmask: bitul N setat = campul N exista
           [daca bitul 31 e setat, urmeaza INCA un cuvant "present" de 4 bytes, s.a.m.d.]
apoi       campurile, in ordinea bitilor, fiecare ALINIAT la marimea lui
```

**Exemplu real** (beacon `HomeNetwork`, 38 bytes):
```
00 00 26 00 | 2f 40 00 a0 | 20 08 00 a0 | 20 08 00 00 | e6 d0 60 00 00 00 00 00 | 10 | 02 | 99 09 a0 00 | a6 | ...
ver pad len | present 0   | present 1   | present 2   | bit 0: TSFT (8 bytes)   |fl. |rate| bit 3: chan | sig
       =38  | bit31 = 1   | bit31 = 1   | bit31 = 0   | offset 16               | 24 | 25 | 26          | 30
```
- `length` = `0x0026` = **38**, deci 802.11 incepe la offset 38;
- **present 0** = `0xa000402f`; in binar, bitii setati sunt **0, 1, 2, 3, 5, 14, 29, 31**. Bitul 31 = mai urmeaza un cuvant present. **present 1** are si el bitul 31, **present 2** nu. Deci sunt 3 cuvinte, iar campurile incep la offset 4 + 3×4 = **16**;
- campurile din cuvantul 0, in ordine:

  | bit | camp | marime | aliniere | offset | valoare |
  |---|---|---|---|---|---|
  | 0 | TSFT (timestamp al placii) | 8 | 8 | 16 | (sarit) |
  | 1 | **Flags** | 1 | 1 | 24 | `0x10` → **bitul FCS e setat**: ultimii 4 bytes ai frame-ului sunt CRC |
  | 2 | Rate | 1 | 1 | 25 | `0x02` = 2 × 500 kbps = 1 Mbps |
  | 3 | **Channel** (frecventa 2 + flags 2) | 4 | 2 | 26 | `99 09` = **2457 MHz** |
  | 5 | **Antenna signal** (dBm, cu semn) | 1 | 1 | 30 | `0xa6` = **−90 dBm** |

**Algoritmul din `RadioTapParser`:**
1. **verifica:**
   - cel putin 8 bytes;
   - `version == 0`;
   - `offset + length <= data.length`;
2. **sare peste cuvintele present:** cat timp bitul 31 e setat, avanseaza 4 bytes (si verifica sa nu iasa din `length`);
3. pentru fiecare bit de la 0 in sus, daca e setat in present 0:
   - daca nu stim marimea campului (`SIZE_TABLE[bit] == 0`) → **ne oprim**. Nu stim unde incepe urmatorul, dar campurile de care avem nevoie (1, 3, 5) au venit deja;
   - **aliniaza:** `rel = (rel + align − 1) / align * align`, adica rotunjeste in sus la un multiplu de `align`. Alinierea se calculeaza fata de **inceputul headerului Radiotap**, nu al frame-ului;
   - citeste daca e bitul 1, 3 sau 5;
   - avanseaza cu marimea campului.

**De ce aliniere:** campurile sunt aliniate "natural", adica un camp de 2 bytes incepe la o pozitie para, unul de 8 la un multiplu de 8. Exemplu: present = doar Flags + Channel. Flags e la `rel 8`; Channel ar fi la 9, dar are aliniere 2, deci sare **un byte de padding** si incepe la 10. Fara aliniere ai citi frecventa decalata cu un byte, adica gunoi.

**Iesire:** `RadioTapHeader(length, hasFrameCheckSequence, freqMHz, signalDBm)`.

**De ce doar cuvantul 0:** placa RTL8822CE trimite semnalul de trei ori (combinat, antena 0, antena 1), ultimele doua in cuvintele extinse. Il folosim pe primul, cel combinat, din cuvantul 0.

### 6.2 Headerul 802.11 (`Dot11Parser`), doar monitor

**Frame Control**, primii 2 bytes (802.11 e **little-endian** si numeroteaza bitii de la cel mai putin semnificativ, B0):
```
byte 0:   b7 b6 b5 b4 | b3 b2 | b1 b0           byte 1:  b7    b6        ... b1      b0
          subtype     | type  | protocol version         Order Protected ... FromDS  ToDS
```
```java
int type    = (byte0 >> 2) & 0x03;   // bitii 2-3: muta-i la dreapta cu 2, pastreaza 2 biti
int subtype = (byte0 >> 4) & 0x0F;   // bitii 4-7: muta-i la dreapta cu 4, pastreaza 4 biti
boolean toDs      = (byte1 & 0x01) != 0;   // bitul 0
boolean fromDs    = (byte1 & 0x02) != 0;   // bitul 1
boolean protected = (byte1 & 0x40) != 0;   // bitul 6
```

**Exemple reale:**

| byte 0 | binar | type | subtype | ce e |
|---|---|---|---|---|
| `0x80` | `1000 00 00` | 0 (management) | 8 | **Beacon** |
| `0x88` | `1000 10 00` | 2 (data) | 8 | **QoS Data** |
| `0x48` | `0100 10 00` | 2 (data) | 4 | **Null** (fara date) |
| `0xd4` | `1101 01 00` | 1 (control) | 13 | **ACK** |

**Structura** (management si data):
```
FC (2) | Duration (2) | Address 1 (6) | Address 2 (6) | Address 3 (6) | Sequence (2) | [QoS Control (2)]
offset 0      2              4               10              16             22            24
```

**Rolul adreselor depinde de ToDS/FromDS** (cursul are acelasi tabel):

| ToDS | FromDS | Address 1 | Address 2 | Address 3 | cand |
|---|---|---|---|---|---|
| 0 | 0 | Destination | Source | BSSID | management (beacon), ad-hoc |
| 1 | 0 | BSSID | Source | Destination | client → AP |
| 0 | 1 | Destination | BSSID | Source | AP → client |
| 1 | 1 | Receiver | Transmitter | Destination | (+Address 4 = Source) punte wireless; **neimplementat**, adresele raman `null` |

**Lungimea headerului**, de care depinde unde incepe urmatorul layer:
- control ACK / CTS (subtype 13 / 12): **10** (FC + Duration + o adresa);
- celelalte control (RTS, Block Ack...): **16** (FC + Duration + doua adrese);
- management si data: **24**;
- data cu bitul QoS (`subtype & 0x08`, deci QoS Data / QoS Null): **+2** pentru QoS Control → 26.

**Validari:**
- minim 10 bytes;
- control non-ACK/CTS: minim 16;
- management/data: minim 24.

**Exemplu real**, beacon-ul (dupa cei 38 de bytes de Radiotap):
```
80 00 | 00 00 | ff ff ff ff ff ff | 50 d4 f7 03 12 f3 | 50 d4 f7 03 12 f3 | 60 af
FC    | dur   | addr1 = Dest      | addr2 = Source    | addr3 = BSSID     | seq
```
ToDS = FromDS = 0, deci Destination = broadcast, Source = BSSID = routerul.

**Limitari cunoscute** (decizie de scope):
- frame-urile cu 4 adrese (ToDS = FromDS = 1) n-au adresele completate;
- bitul Order (+4 bytes HT Control) nu e tratat. Un astfel de frame e decodat gresit dupa headerul 802.11 si de obicei ajunge la `parseErrors`.

### 6.3 Corpul beacon-ului si Information Elements (`BeaconParser`, `InformationElementParser`)

Dupa headerul de 24 de bytes, un beacon (si un probe response, subtype 5) are:
```
Timestamp (8) | Beacon interval (2, LE) | Capability info (2, LE) | Information Elements ...
```
**Exemplu real:**
```
84 e1 42 b8 0a 01 00 00 | 64 00 | 31 04
timestamp               | 100   | 0x0431 → bitul 4 (0x0010) setat → Privacy = da
```
- **Beacon interval** = 100 TU (Time Unit = 1024 µs), adica un beacon la ~102 ms;
- **Privacy** (bitul 4 din Capability) = reteaua cere criptare.

**Information Elements** = **TLV** (Type-Length-Value), unul dupa altul pana la final:
```
ID (1) | Length (1) | Value (Length bytes) | ID | Length | Value | ...
```
`InformationElementParser`:
- cat timp `pos + 2 <= end`: citeste `id`, `length`;
- verifica `pos + 2 + length <= end` (altfel "IE is truncated");
- salveaza `IE(id, offsetValoare, length)` si sare cu `2 + length`.

`end` = finalul frame-ului **minus 4** daca Radiotap a spus ca exista FCS. Altfel CRC-ul ar fi citit ca IE.

**Primele IE-uri reale din beacon:**
```
00 0b 48 6f 6d 65 4e 65 74 77 6f 72 6b   → IE 0 (SSID), 11 bytes: "HomeNetwork"
01 08 82 84 8b 96 24 30 48 6c            → IE 1 (Supported rates)
03 01 0a                                 → IE 3 (DS Parameter Set), 1 byte: canalul 10
...
30 18 01 00 00 0f ac 02 ...              → IE 48 (RSN), 24 bytes
...
dd 1a 00 50 f2 01 ...                    → IE 221 (Vendor), OUI 00:50:f2 tip 1 = WPA
```

`BeaconParser` ia:
- **SSID** din IE 0, ca UTF-8. Gol (`""`) inseamna retea ascunsa;
- **canalul** din IE 3, cu lungime ≥ 1. Daca lipseste, din IE 61 (HT Operation, primul byte = canalul primar). Daca lipsesc amandoua, `null`.

**De ce canalul din beacon si nu din frecventa Radiotap:** placa acordata pe canalul 11 prinde si beacon-uri de pe 10 sau 12, pentru ca canalele de 2.4 GHz se suprapun. Frecventa din Radiotap spune unde **asculta placa**; IE 3 spune unde **emite AP-ul**.

### 6.4 Securitatea (`SecurityClassifier`)

| ce gasim in beacon | rezultat |
|---|---|
| nici RSN (IE 48), nici WPA (IE 221 cu `00 50 f2 01`) | `WEP` daca bitul Privacy e setat, altfel `Open` |
| RSN si WPA | `WPA/WPA2-PSK` (sau `-Enterprise`) |
| RSN cu AKM 2 (PSK) **si** 8 (SAE) | `WPA2/WPA3` (tranzitie) |
| RSN cu AKM 8 | `WPA3-SAE` |
| doar RSN | `WPA2-PSK` / `WPA2-Enterprise` |
| doar WPA | `WPA-PSK` / `WPA-Enterprise` |

**Structura RSN** (si WPA, dupa cei 4 bytes `00 50 f2 01`):
```
Version (2) | Group cipher (4) | Pairwise count (2) | Pairwise list (4 × n) | AKM count (2) | AKM list (4 × m) | ...
```
- un "suite" are 4 bytes: **OUI (3) + tip (1)**. Ne intereseaza doar tipul, al 4-lea byte;
- **AKM** (Authentication and Key Management): **1** = 802.1X (Enterprise), **2** = PSK (parola), **8** = SAE (WPA3).

**Exemplu real** (RSN din `HomeNetwork`):
```
01 00 | 00 0f ac 02 | 02 00 | 00 0f ac 04  00 0f ac 02 | 01 00 | 00 0f ac 02
v1    | group: TKIP | 2 x   | CCMP         TKIP        | 1 x   | AKM 2 = PSK
```
Plus IE-ul WPA, deci `WPA/WPA2-PSK`, exact ce apare in tabel.

### 6.5 Canal ↔ frecventa (`ChannelMath`) si producatorul (`OuiDatabase`)

- **2.4 GHz:** canalele sunt la 5 MHz unul de altul, cu canalul 1 la 2412: `f = 2407 + 5 × canal` (1–13). Canalul 14 e special: 2484;
- **5 GHz:** `f = 5000 + 5 × canal`;
- **OUI:**
  - primii 3 bytes ai unui MAC sunt alocati de IEEE unui producator (`50:d4:f7` = TP-Link). Lista e `oui.txt`, inclusa in proiect, fara cereri pe internet;
  - **bitul `0x02` din primul byte** (bitul "U/L") setat = adresa **administrata local**, adica un MAC aleator (telefoanele moderne fac asta). Nu are producator, deci afisam `Local/Unknown`.

### 6.6 Ethernet (`EthernetHeaderParser`), doar managed

```
Destination MAC (6) | Source MAC (6) | EtherType (2, BIG-endian) | payload
```
**Exemplu real** (GET-ul):
```
50 d4 f7 03 12 f4 | 80 91 33 50 03 49 | 08 00
router            | laptop            | 0x0800 = IPv4
```
- **VLAN:** daca EtherType = `0x8100`, urmeaza un tag de 4 bytes, iar EtherType-ul real e la offset 16 (header de 18);
- **validari:** minim 14 bytes (18 cu VLAN).

**De ce Ethernet si nu 802.11 in managed:** placa si driverul fac deja treaba de 802.11 (asociere, decriptare WPA2). Kernel-ul preda pachetele mai departe ca si cum ar fi venit pe un cablu Ethernet. libpcap ne spune asta prin link type 1. Din acest motiv UI-ul scrie "Ethernet (managed mode)".

### 6.7 LLC/SNAP (`LlcHeaderParser`), doar monitor, frame-uri de date necriptate

802.11 nu are camp EtherType. Il imprumuta de la standardul **802.2 LLC**, cu extensia **SNAP**:
```
AA | AA | 03 | 00 00 00     | 08 00
DSAP SSAP ctrl organization   EtherType (big-endian)
└──── LLC ───┘└────────── SNAP ──────────┘
```
- `AA AA 03` = "urmeaza SNAP"; `00 00 00` = "ce urmeaza e un EtherType normal";
- **validari:**
  - minim 8 bytes;
  - DSAP **si** SSAP trebuie sa fie `0xAA`, altfel nu e SNAP (de ex. `42 42` = Spanning Tree);
- dupa el, drumul e identic cu managed: `0x0800` → IPv4.

### 6.8 IPv4 (`Ipv4HeaderParser`), **toate campurile**

Tot IP-ul e **big-endian**. Diagrama din RFC 791 (fiecare rand = 32 de biti = 4 bytes):
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Version|  IHL  |    DSCP   |ECN|          Total Length         |  bytes 0-3
|         Identification        |Flags|     Fragment Offset     |  bytes 4-7
|  Time to Live |    Protocol   |         Header Checksum       |  bytes 8-11
|                       Source Address                          |  bytes 12-15
|                    Destination Address                        |  bytes 16-19
|                    Options (daca IHL > 5)                     |  bytes 20..
```
**Exemplu real** (GET-ul, offset 14 in frame):
```
45 | 00 | 00 7f | c4 49 | 40 00 | 40 | 06 | 15 d6 | c0 a8 00 a5 | 22 df 7c 2d
```

| camp | cum il citim | calcul pe exemplu | valoare |
|---|---|---|---|
| Version | `byte0 >> 4` (bitii de sus) | `0x45 >> 4` | **4** |
| IHL | `(byte0 & 0x0F) * 4` (bitii de jos, in cuvinte de 4 bytes) | `5 × 4` | **20 bytes** |
| DSCP | `byte1 >> 2` (6 biti de sus) | `0x00 >> 2` | 0 |
| ECN | `byte1 & 0x03` (2 biti de jos) | | 0 |
| Total length | u16 BE @2 | `0x007f` | **127** |
| Identification | u16 BE @4 | `0xc449` | 50249 |
| Flags + Fragment offset | u16 BE @6 = `0x4000` | | |
| ↳ Don't Fragment | `& 0x4000` | setat | **da** |
| ↳ More Fragments | `& 0x2000` | | nu |
| ↳ Fragment offset | `& 0x1FFF` (13 biti de jos) | | 0 |
| TTL | u8 @8 | `0x40` | **64** |
| Protocol | u8 @9 | `0x06` | **6 = TCP** |
| Header checksum | u16 BE @10 | | `0x15d6` (afisat, neverificat: scos din scope) |
| Source | 4 bytes @12 | `c0.a8.00.a5` | **192.168.0.165** |
| Destination | 4 bytes @16 | `22.df.7c.2d` | **34.223.124.45** |
| Options | bytes 20 .. IHL×4 | IHL = 5 | niciuna (afisate ca hex brut) |

**Validari:**
- minim 20 bytes;
- `version == 4`;
- `IHL × 4 >= 20` si headerul incape in date.

### 6.9 TCP (`TcpHeaderParser`), **toate campurile**

Diagrama din RFC 9293:
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
|          Source Port          |       Destination Port        |  0-3
|                        Sequence Number                        |  4-7
|                    Acknowledgment Number                      |  8-11
|  Data |       |C|E|U|A|P|R|S|F|                               |
| Offset| Rsrvd |W|C|R|C|S|S|Y|I|            Window             |  12-15
|       |       |R|E|G|K|H|T|N|N|                               |
|           Checksum            |         Urgent Pointer        |  16-19
|                    Options (daca Data Offset > 5)             |  20..
```
**Exemplu real** (GET-ul, offset 34):
```
99 1a | 00 50 | 2b 9c d8 b9 | 3b 39 6d a4 | 80 | 18 | 00 3f | 24 3e | 00 00 | 01 01 08 0a ...
```

| camp | cum il citim | valoare |
|---|---|---|
| Source port | u16 BE @0 | `0x991a` = **39194** |
| Destination port | u16 BE @2 | `0x0050` = **80** |
| Sequence number | u32 BE @4 (in `long`) | 731699385 |
| Acknowledgment number | u32 BE @8 | 993619364 |
| Data offset (lungimea headerului) | `(byte12 >> 4) * 4` | `0x80 >> 4` = 8 → **32 bytes** |
| NS | `byte12 & 0x01` | 0 |
| CWR ECE URG ACK PSH RST SYN FIN | `byte13 & 0x80`, `0x40`, … `0x01` | `0x18` = `0x10` + `0x08` = **ACK + PSH** |
| Window | u16 BE @14 | 63 (valoare bruta, vezi mai jos) |
| Checksum | u16 BE @16 | `0x243e` |
| Urgent pointer | u16 BE @18 | 0 |
| Options | bytes 20 .. data offset | `01 01 08 0a ...` (NOP, NOP, Timestamp), afisate ca hex |

**Validari:**
- minim 20 bytes;
- data offset ≥ 20 si headerul incape in date.

**Window = 63 pare mic.** E valoarea **bruta** din header. Conexiunea a negociat "window scaling" in SYN (optiunea 3), deci fereastra reala e `63 × 2^scale`. Wireshark arata ambele ("Window: 63", "Calculated window size"). Noi afisam campul asa cum e pe fir, pentru ca decodarea optiunilor e scoasa din scope.

### 6.10 HTTP (`HttpPayloadParser`)

**Cand:** sursa **sau** destinatia e portul 80, si `payloadLength > 0`. SYN-ul si ACK-urile goale n-au payload.

**Cum:**
- verifica `length >= 0` si `offset + length <= data.length`;
- pentru fiecare byte:
  - printabil ASCII (`0x20`..`0x7E`) sau `\r` `\n` `\t` → caracterul;
  - altfel → `.`;
- **`\r\n` sunt pastrate**: HTTP isi separa liniile prin CRLF (RFC 9112), iar asa cererea apare pe linii, ca in Wireshark.

**Exemplu real** (offset 66 = 14 + 20 + 32, lungime 127 − 20 − 32 = **75**):
```
47 45 54 20 2f 20 48 54 54 50 2f 31 2e 31 0d 0a  →  "GET / HTTP/1.1\r\n"
```

---

## 7. Registry-ul de AP-uri (`AccessPointRegistry`)

`update(data, radioTap, dot11, beacon, now)`:
- **cheia** e BSSID-ul (`ConcurrentHashMap`), deci un AP = o intrare, actualizata la fiecare beacon;
- canalul vine din beacon (§6.3), apoi din frecventa Radiotap ca rezerva. Daca tot nu exista, beacon-ul e ignorat;
- frecventa se calculeaza **din canal** (`ChannelMath`), nu din Radiotap (acelasi motiv: scurgerea de pe canalele vecine);
- semnalul vine din Radiotap, securitatea din §6.4, producatorul din §6.5;
- `lastSeenMillis = now`.

`snapshot(now)`:
- sterge AP-urile nevazute de **30 s**;
- sorteaza dupa semnal, cel mai puternic primul;
- intoarce o copie imutabila.

`ConcurrentHashMap` pentru ca scrie thread-ul de captura si citeste scheduler-ul.

---

## 8. Pachetele: `PacketStore` → `PacketViewMapper` → WebSocket / REST

**`PacketStore`** (`service/sniffer/`):
- `ArrayDeque` cu ultimele **5000** de pachete, fiecare intr-un `StoredPacket(id, packet)`, cu `id` crescator de la 1;
- `add` intoarce id-ul. Peste 5000, `removeFirst()`;
- `get(id)`: pachetul, sau `null` daca a iesit din buffer;
- `after(lastId, max)`: pachetele cu id > `lastId`, cel mai vechi primul, cel mult `max`;
- toate metodele sunt `synchronized`: scrie thread-ul de captura, citesc thread-ul scheduler si thread-urile HTTP, iar `ArrayDeque` nu e thread-safe.

**`PacketViewMapper`** (`api/`) doar **formateaza text**: bytes-ii au fost cititi deja de parsere. Face doua vederi:
- **`toSummary`**: randul din lista: `protocol` (cel mai inalt layer decodat), `source` / `destination` (IP daca exista IPv4, altfel MAC), `info` (`GET / HTTP/1.1`, `39194 → 80 [SYN]`, `SSID=HomeNetwork CH=10`, `Protected data`...);
- **`toDetail`**:
  - lista de layere, fiecare cu `name`, `offset`, `length` (calculate la fel ca in decoder) si campurile formatate (`"0xc449 (50249)"`, `"20 bytes"`, `yes`/`no`);
  - `hex`: tot frame-ul in hex.

**`UpdatesPublisher`** (thread-ul scheduler, `@Scheduled(fixedRate = 200)`), la fiecare 200 ms:
1. daca nu e niciun browser conectat → nu face nimic;
2. construieste `UpdateMessage`:
   - `status`: `running`, sursa, modul, canalul curent si cel fixat, `frames`, `parseErrors`;
   - `aps` = `registry.snapshot()`;
   - `packets` = `packetStore.after(lastSentPacketId, 500)` → `toSummary`;
3. tine minte ultimul id trimis. Ce depaseste 500 pleaca la urmatorul tick;
4. JSON (Jackson) → `UpdatesWebSocketHandler.broadcast` → fiecare browser conectat.

**De ce batching la 200 ms:** un mesaj per pachet ar insemna sute de mesaje pe secunda si un browser blocat. Asa sunt 5 mesaje pe secunda, oricat trafic ar fi.

**`GET /api/packets/{id}`** (`PacketController`): `packetStore.get(id)` → `toDetail`. Daca nu mai e in buffer → `PacketNotFoundException` → **404** `{"error": "Packet 1 is no longer in the buffer"}`.

---

## 9. Frontend-ul

- **`useLiveUpdates`:**
  - deschide `ws://localhost:5173/ws/updates` (proxy-ul Vite il duce la backend);
  - la fiecare mesaj pastreaza `latestUpdate` si **adauga** `message.packets` la lista, maxim 1000;
  - daca backend-ul cade, reincearca la 2 s.
- **`App`** imparte datele:
  - `status` → header si `ControlPanel`;
  - `aps` → `ChannelGraph` + `AccessPointTable`;
  - `packets` → `PacketList`;
  - id-ul selectat → `PacketInspector`.
- **`ControlPanel`:** butoanele cheama REST (`api.ts`): mode, start / stop, replay, pin channel.
- **`ChannelGraph`:** SVG desenat de mana, cate un "deal" per AP, intins pe ±2 canale (latimea reala a unui canal de 20 MHz), inalt cat semnalul.
- **`AccessPointTable`:** cele 7 campuri si iconita colorata (verde ≥ −60 dBm, galben −60…−85, rosu < −85).
- **`PacketList`:** tabel, cel mai nou jos. Urmareste pachetele noi cat timp esti jos in lista.
- **`PacketInspector`:** la click cere `/api/packets/{id}` si arata arborele de layere. Click pe un layer → `HexDump` coloreaza bytes-ii `[offset, offset + length)`.

---

## 10. Thread-uri si date partajate

| thread | ce face | ce scrie | ce citeste |
|---|---|---|---|
| **capture** | `liveLoop` / `fileReplayLoop` → `processFrame` | `frames`, `parseErrors` (`AtomicLong`), `PacketStore` (`synchronized`), registry (`ConcurrentHashMap`) | `running` (`volatile`) |
| **channel-hopper** | `iw set channel` la 250 ms | `currentChannel` (`synchronized`) | `pinnedChannel` |
| **scheduler** | `UpdatesPublisher.publish` la 200 ms | `lastSentPacketId` (doar el il atinge) | tot statusul, registry, store |
| **HTTP** (Tomcat) | request-urile REST | `running` prin `start` / `stop` (`synchronized`), `pinnedChannel` | store (`get`) |

Regula: **orice camp atins de doua thread-uri** e protejat (`volatile`, `synchronized`, `Atomic*` sau o colectie concurenta). Obiectele care trec dintre thread-uri (`RawFrame`, headerele, `DecodedPacket`) sunt **imutabile** (`@Value`), deci nu au nevoie de lock.

---

## 11. Toate validarile intr-un singur tabel

Orice validare picata = `IllegalArgumentException` → prinsa in `processFrame` → `parseErrors++` → frame-ul e ignorat, captura continua.

| parser | ce verifica |
|---|---|
| `ByteReader` | orice citire in afara array-ului (`checkFromIndexSize`) |
| `PcapFileReader` | fisier ≥ 24 bytes; magic cunoscut (refuza pcapng); header de frame complet (16); frame complet (`incl_len`) |
| `RadioTapParser` | ≥ 8 bytes; `version == 0`; `length` incape in frame; cuvintele `present` incap in `length` |
| `Dot11Parser` | ≥ 10 bytes; control non-ACK/CTS ≥ 16; management/data ≥ 24 |
| `InformationElementParser` | fiecare IE incape pana la `end` (FCS exclus) |
| `BeaconParser` | ≥ 12 bytes de corp fix; IE 3 / 61 folosite doar cu lungime ≥ 1 |
| `SecurityClassifier` | IE WPA doar cu lungime ≥ 4 si `00 50 f2 01`; liste citite doar cat incap |
| `EthernetHeaderParser` | ≥ 14 bytes (18 cu VLAN) |
| `LlcHeaderParser` | ≥ 8 bytes; DSAP = SSAP = `0xAA` |
| `Ipv4HeaderParser` | ≥ 20 bytes; `version == 4`; `20 <= IHL × 4` si incape |
| `TcpHeaderParser` | ≥ 20 bytes; `20 <= data offset × 4` si incape |
| `HttpPayloadParser` | `length >= 0`; `offset + length` incape |
| `PacketDecoder` | link type 1 sau 127, altfel exceptie |
| `InterfaceService` | nume de interfata valid + interfata wireless reala; canal in lista 1–13 |
| `ReplayFiles` | fisierul de replay trebuie sa fie **in lista**, deci nu se poate cere `../../etc/shadow` |

**Ce NU verificam (decizie de scope):** checksum-urile IPv4/TCP, CRC-ul FCS, decodarea optiunilor IP/TCP, UDP, IPv6.

---

## 12. "De unde stii cum se citesc bitii?"

### 12.1 Metoda generala (o aplici la orice camp)

Fiecare protocol are in standardul lui o **diagrama a headerului**. De acolo:

1. **offset-ul:** in ce byte incepe campul (randul din diagrama × 4 + pozitia pe rand / 8);
2. **marimea:** cati biti / bytes are;
3. **ordinea bytes-ilor** (endianness): scrisa in standard. IP / TCP / Ethernet = big-endian ("network byte order"); Radiotap si campurile 802.11 = little-endian;
4. **masca (`&`):** pastreaza doar bitii campului. Un camp de N biti → masca cu N de 1: 2 biti → `0x03`, 4 biti → `0x0F`, 13 biti → `0x1FFF`;
5. **shift-ul (`>>`):** cu cate pozitii trebuie mutat campul ca bitul lui cel mai de jos sa ajunga pe pozitia 0. = numarul de biti care sunt **sub** el in byte / cuvant;
6. **unitatea:** unele campuri nu sunt in bytes. IHL si Data Offset sunt in **cuvinte de 32 de biti** (× 4), Fragment Offset in unitati de 8 bytes, Beacon interval in TU de 1024 µs.

**Exemplu complet, IHL:**
- RFC 791 deseneaza `Version` in bitii 0–3 si `IHL` in bitii 4–7 ai primului byte;
- RFC-urile numeroteaza bitii **de la stanga** (bitul 0 = cel mai semnificativ). Deci IHL e jumatatea **de jos** a byte-ului;
- 4 biti → masca `0x0F`. Fiind deja jos → shift 0;
- RFC-ul spune ca IHL e "the length of the internet header in **32 bit words**" → × 4;
- deci `(byte0 & 0x0F) * 4`. Pe `0x45`: `5 × 4 = 20`.

**Exemplu complet, Type din 802.11:**
- standardul 802.11 numeroteaza bitii **de la dreapta**: B0 = cel mai putin semnificativ. Frame Control: B0–B1 = Protocol Version, **B2–B3 = Type**, B4–B7 = Subtype;
- Type are 2 biti sub el (B0, B1) → **shift 2**; are 2 biti → **masca `0x03`**;
- deci `(byte0 >> 2) & 0x03`. Pe `0x88` (`1000 1000`): `>> 2` = `0010 0010`, `& 0x03` = `10` = **2** (data).

**Exemplu complet, flag-urile Don't Fragment / More Fragments din IPv4:**
- Flags sunt primii 3 biti ai cuvantului de 16 biti de la offset 6 (bit 0 rezervat, bit 1 DF, bit 2 MF, numarati de la stanga);
- intr-un `u16` big-endian, bitul cel mai din stanga are valoarea `0x8000`, urmatorul `0x4000`, apoi `0x2000`;
- deci DF = `& 0x4000`, MF = `& 0x2000`, iar restul de 13 biti, `& 0x1FFF` = Fragment Offset.

**Capcana numerotarii:**
- **RFC-urile (IP, TCP)** numara bitii de la **stanga** (MSB = 0);
- **IEEE 802.11** numara de la **dreapta** (B0 = LSB).

Masca si shift-ul se scriu mereu in functie de **valoarea** bitului, nu de numarul lui din diagrama. De aceea, la fiecare camp, te uiti intai cum numeroteaza standardul.

### 12.2 Surse, pe layer

| layer | sursa oficiala | ce gasesti acolo |
|---|---|---|
| libpcap (API) | `man pcap`, `man pcap_create`, `man pcap_next_ex` (pe laptop, din `libpcap-dev`) sau https://www.tcpdump.org/manpages/ | semnaturile C, codurile de retur (1 / 0 / −1 / −2), `struct pcap_pkthdr` |
| link type 1 / 127 | https://www.tcpdump.org/linktypes.html | `LINKTYPE_ETHERNET = 1`, `LINKTYPE_IEEE802_11_RADIOTAP = 127` |
| fisierul `.pcap` | `man pcap-savefile` (https://www.tcpdump.org/manpages/pcap-savefile.5.html) | header global 24 bytes, magic, header de frame 16 bytes |
| FFM (Java) | JEP 454: https://openjdk.org/jeps/454 | `Linker`, `SymbolLookup`, `FunctionDescriptor`, `Arena`, `MemorySegment` |
| Radiotap | https://www.radiotap.org (pagina principala + "Defined fields") | header, `present` + bitul 31, **alinierea naturala**, little-endian, lista campurilor cu marime / aliniere (de acolo vin `SIZE_TABLE` si `ALIGN_TABLE`), bitul FCS (`0x10`) din Flags |
| 802.11 (header, beacon, IE-uri, RSN) | **IEEE Std 802.11** (editia 2020), gratuit prin programul **IEEE GET** (IEEE Xplore). Clauza 9 "Frame formats": Frame Control, campurile de adresa, formatul beacon-ului, "Elements" (SSID, DSSS Parameter Set, HT Operation, RSNE, Vendor Specific). Numerele de sectiune difera intre editii, deci cauta dupa titlu | type / subtype, bitii ToDS / FromDS / Protected, rolul adreselor, QoS Control, Capability (bitul Privacy), ID-urile IE-urilor, structura RSN si valorile AKM |
| tabelul de adrese ToDS / FromDS | si in **cursul** (`Course-notes.pdf`, partea de 802.11; vezi `docs/course-notes-summary.md`) | acelasi tabel ca in §6.2 |
| canale ↔ frecvente | IEEE 802.11, capitolul de PHY pentru 2.4 GHz (DSSS); rezumat accesibil: articolul Wikipedia "List of WLAN channels" | 2412 + 5 × (n − 1), canalul 14 = 2484 |
| OUI / bitul U/L | https://standards-oui.ieee.org/oui/oui.txt; IEEE Std 802 (Overview and Architecture) pentru bitii U/L si I/G | `50-D4-F7 (hex) TP-LINK...`; bitul `0x02` = local, `0x01` = multicast |
| WPA (IE 221, `00:50:F2` tip 1) | specificatia WPA a Wi-Fi Alliance (nu e publica liber). Referinta practica: Wireshark il decodeaza identic ("Vendor Specific: Microsoft Corp.: WPA Information Element") | OUI-ul Microsoft `00:50:F2`, tip 1 = WPA, restul ca RSN |
| Ethernet II | RFC 894 (IP over Ethernet), IEEE 802.3; tag-ul VLAN `0x8100` = IEEE 802.1Q | dst (6), src (6), EtherType (2) |
| LLC/SNAP | **RFC 1042** (IP over IEEE 802 networks), IEEE 802.2 | exact `AA AA 03 00 00 00` + EtherType |
| IPv4 | **RFC 791**, sectiunea 3.1 (diagrama); RFC 2474 (DSCP); RFC 3168 (ECN) | toate campurile, IHL in cuvinte de 32 de biti, flag-urile, ordinea "network byte order" |
| TCP | **RFC 9293**, sectiunea 3.1 (diagrama; inlocuieste RFC 793); RFC 3168 (CWR / ECE); RFC 3540 (NS, experimental) | porturi, seq / ack, Data Offset in cuvinte de 32 de biti, flag-urile din byte-ul 13 |
| HTTP/1.1 | **RFC 9112** | linii terminate cu CRLF (`\r\n`), linia de cerere `GET / HTTP/1.1` |
| ASCII | `man ascii` | `0x20` = spatiu, `0x7E` = `~`, `0x0D` = `\r`, `0x0A` = `\n` |

Pentru un singur loc de pornire, IP / TCP / Ethernet sunt explicate pe larg in **"TCP/IP Illustrated, Vol. 1"** (Stevens / Fall). Pentru Wi-Fi, in **"802.11 Wireless Networks: The Definitive Guide"** (Gast).

### 12.3 Cum verifici ca ai citit bine: Wireshark

Asta e cel mai bun raspuns practic, pentru ca **asa s-a si lucrat**:
1. deschizi fixture-ul (de ex. `managed-http.pcap`) in Wireshark;
2. click pe un camp, de ex. "Header Length": Wireshark **coloreaza bytes-ii** in hex si arata **masca** sub forma `.... 0101 = Header Length: 20 bytes (5)`. Asta iti spune direct ca e jumatatea de jos a byte-ului si ca se inmulteste cu 4;
3. testele noastre (`Ipv4HeaderParserTest`, `TcpHeaderParserTest`, `PacketViewMapperTest`...) compara cu **exact valorile pe care le arata Wireshark** pe aceleasi pachete reale: TTL 64, identification `0xc449`, seq 731699385, flags PSH + ACK.

Live, langa aplicatie: `sudo tcpdump -i wlp4s0 -nn -vv -X port 80` afiseaza aceleasi campuri si hex-ul.

### 12.4 Intrebari probabile si ce raspunzi

**"De unde stii ca IHL se inmulteste cu 4?"**
RFC 791 defineste IHL ca lungimea headerului **in cuvinte de 32 de biti**. 32 de biti = 4 bytes. IHL = 5 → 20 bytes, minimul fara optiuni. La fel la TCP: Data Offset e tot in cuvinte de 32 de biti (RFC 9293).

**"De ce `>> 2` si `& 0x03` la type-ul 802.11?"**
In standardul 802.11, Type ocupa bitii B2–B3 din Frame Control (B0 = cel mai putin semnificativ). Shift 2 il aduce pe pozitiile 0–1, masca `0x03` pastreaza doar acei 2 biti.

**"De ce `& 0xFF`?"**
In Java `byte` are semn. `0xC0` ar fi −64. `& 0xFF` il face `int` 0..255. Singura exceptie e semnalul din Radiotap, care chiar e un numar cu semn (dBm), deci il citim cu semn.

**"De ce Radiotap e little-endian si IP big-endian?"**
Asa scrie in fiecare standard. radiotap.org: toate campurile sunt little-endian. RFC 791: ordinea de transmisie e big-endian ("network byte order"). De aceea `ByteReader` are metode separate si nu folosim niciodata ordinea masinii.

**"Ce e alinierea in Radiotap?"**
Fiecare camp incepe la un multiplu al marimii lui, socotit de la inceputul headerului Radiotap (radiotap.org, "alignment"). Inainte de fiecare camp rotunjim pozitia in sus: `(rel + align − 1) / align * align`.

**"Cum stii unde se termina headerul 802.11?"**
Din type / subtype: ACK / CTS = 10, alte control = 16, management / data = 24, plus 2 daca e QoS data (bitul `0x08` din subtype).

**"Cum stii ca e beacon?"**
Type 0 (management), subtype 8. Probe response e subtype 5 si are acelasi corp.

**"De unde iei SSID-ul si canalul?"**
Din Information Elements, format TLV: SSID = IE 0, canalul = IE 3 (DSS Parameter Set), rezerva IE 61 (HT Operation).

**"De ce nu iei canalul din frecventa Radiotap?"**
Canalele de 2.4 GHz se suprapun, deci placa acordata pe 11 prinde si beacon-uri de pe 10 sau 12. Radiotap spune unde asculta placa; IE 3 spune unde emite AP-ul.

**"Cum detectezi WPA2 / WPA3?"**
IE 48 (RSN) → lista de AKM (al 4-lea byte al fiecarui suite): 2 = PSK, 8 = SAE (WPA3), 1 = 802.1X. WPA vechi = IE 221 cu OUI `00:50:F2` tip 1. Nimic din astea + bitul Privacy = WEP; fara Privacy = Open.

**"De ce in monitor mode nu vezi IP-ul pe reteaua ta?"**
E WPA2: tot ce e dupa headerul 802.11 e criptat (CCMP), iar bitul Protected e setat. Vedem MAC-urile, dar nu continutul. Pe o retea deschisa vedem tot: LLC/SNAP → IPv4 → TCP → HTTP.

**"De ce in managed mode nu ai header 802.11?"**
Kernel-ul si driverul au facut deja asocierea si decriptarea, iar pachetele sunt predate ca Ethernet. libpcap raporteaza link type 1. Vedem doar traficul laptopului, dar decriptat.

**"Cat face libpcap si cat faci tu?"**
libpcap: deschide interfata si ne da bytes-ii cu un timestamp. **Nu** folosim filtrele (BPF) si niciun parser din libpcap sau din alta biblioteca. Si fisierele `.pcap` le citim cu un reader scris de mana. De la `byte[]` incolo, tot e cod propriu.

**"De ce lungimea payload-ului HTTP vine din IPv4 si nu din lungimea frame-ului?"**
Frame-ul poate avea la final padding (Ethernet umple pana la 60 de bytes) sau FCS (4 bytes CRC in monitor). `Total Length` din IPv4 e lungimea exacta a pachetului IP.

**"Ce e FCS?"**
Frame Check Sequence: CRC-32 de 4 bytes la finalul frame-ului 802.11. Radiotap ne spune prin bitul `0x10` din Flags daca placa l-a lasat in frame. Daca da, il excludem cand citim IE-urile.

**"Ce e NS?"**
Nonce Sum, ultimul bit din byte-ul 12 al headerului TCP (RFC 3540, experimental, practic nefolosit). Il afisam pentru ca cerinta e "toate campurile".

**"Window e 63, nu e prea mic?"**
E valoarea bruta din header. Conexiunea foloseste window scaling (negociat in SYN), deci fereastra reala e 63 × 2^scale. Optiunile TCP le afisam ca hex, fara decodare (decizie de scope).

**"Ce se intampla cu un pachet stricat?"**
Parserul arunca `IllegalArgumentException`. `processFrame` o prinde, creste `parseErrors` (vizibil in UI), iar captura merge mai departe.

**"Cum ai testat?"**
Teste unitare pe **capturi reale** din reteaua proprie (fixtures), plus byte-arrays construite de mana pentru cazurile limita (aliniere, cuvinte present extinse, QoS, header trunchiat). Valorile asteptate sunt cele din Wireshark pe aceleasi pachete. Total: 193 de teste.
