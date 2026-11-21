# Informații utile pentru proiect: Course-notes.pdf, p. 1–73

Doar ce te ajută la proiect (Wi-Fi Analyzer + packet sniffer), din tot capitolul 802.11, până la „WPA and WPA-2” (p. 73). Restul PDF-ului nu e relevant.
Fiecare secțiune începe cu **„La ce folosește”**. Teoria pură (Fourier, Huffman/LZW, GSM/LTE etc.) e omisă.

---

## 1. Undele radio și semnalul (p. 1–10)

**La ce folosește:** ca să înțelegi de ce signal strength-ul (dBm) afișat pentru fiecare rețea variază și ce înseamnă valorile.

- Wi-Fi folosește **microwaves** din benzile **ISM** (Industrial, Scientific, Medical), care sunt libere, fără licență: **2,4 GHz** și **5 GHz**.
  - La 2,4 GHz, wavelength-ul e de aproximativ 12,5 cm (c = f · λ). Frecvența mare permite antene mici, dar acoperire mai mică.
- Spectrul e împărțit în **channels**: intervale de frecvență fixe, fiecare cu bandwidth-ul lui. Pe channels diferite se poate transmite în paralel, independent.
- Pe drum, semnalul suferă **attenuation**:
  - **reflection** (pereți)
  - **refraction**, **scattering**
  - **absorption** (corpuri, apă, umiditate)
  - **interference** de la alte dispozitive: Bluetooth și cuptorul cu microunde, ambele pe 2,4 GHz
- **Consecință practică:** signal strength-ul unui AP fluctuează de la un beacon la altul. În UI e bine să afișezi o valoare **netezită** (de ex. media ultimelor câteva beacons), nu ultima valoare brută.

### Signal strength (dBm), nu e în notițe, dar îl afișezi
- Radiotap îți dă **antenna signal în dBm**: un număr negativ (byte signed). Cu cât e mai aproape de 0, cu atât semnalul e mai puternic.
- Scara e logaritmică: −3 dB înseamnă jumătate din putere, −10 dB înseamnă o zecime.

| dBm | Calitate | Culoare în UI (pragurile noastre) |
|---|---|---|
| −30 … −60 | Excelent / bun | 🟢 green (≥ −60) |
| −60 … −75 | OK / slab | 🟡 yellow |
| < −75 | Foarte slab | 🔴 red |

---

## 2. Concepte de comunicație digitală (p. 11–47), doar ce contează

**La ce folosește:** vocabular care apare în Radiotap și în afișarea datelor.

- **Symbol:** o bucată scurtă de undă care reprezintă câțiva biți. Placa transformă symbols în bytes înainte să ajungă la tine. **Nu vezi niciodată symbols.**
- **Modulation:** cum pui biții pe carrier, schimbând amplitude (ASK), frequency (FSK) sau phase (PSK), ori amplitude + phase (QAM). Mai mulți biți per symbol înseamnă viteză mai mare, dar ai nevoie de semnal mai curat.
- **Channel capacity / data rate / BER:**
  - Viteza reală e mereu sub capacitatea channel-ului.
  - Când semnalul e slab, placa scade **data rate**-ul (rate adaptation).
  - Radiotap are câmpul **Rate** (în unități de 500 kbps) și, opțional, **antenna noise** (dBm). SNR = signal − noise.
- **ASCII (fixed-length code, 1 byte/caracter):** **SSID-ul** și **payload-ul HTTP** sunt bytes ASCII/UTF-8. La afișare, bytes-ii neprintabili (< 0x20 sau > 0x7E) se arată ca `.`, exact ca în Wireshark.

---

## 3. Rețele 802.11: noțiuni de bază (p. 48–49)

**La ce folosește:** ce afișezi în Wi-Fi Analyzer.

- **Infrastructure:**
  - **BSS:** un **AP** (Access Point) + clienții lui.
  - **ESS:** mai multe AP-uri cu același SSID, legate printr-un **DS** (Distribution System, de obicei Ethernet).
- **Ad-hoc:** dispozitive conectate direct, fără AP.
- **SSID:** numele rețelei, **maxim 32 bytes**. Poate fi gol, în cazul **hidden networks** (SSID de lungime 0). Afișează-le ca „<hidden>”.
- **BSSID:** **MAC address-ul AP-ului**. E cheia unică după care grupezi rețelele în analyzer (un SSID poate avea mai multe BSSID-uri într-un ESS).
- **TCP/IP model:** 802.11 acoperă doar **Physical + Data Link**. IP, TCP și HTTP vin deasupra, iar sniffer-ul le parsează pe toate.

---

## 4. Versiunile 802.11 (p. 49–53)

**La ce folosește:** să știi ce benzi și ce viteze vei vedea în capturi.

| Standard | An | Banda | Viteză max | Modulation |
|---|---|---|---|---|
| 802.11 | 1997 | 2,4 GHz | 2 Mbps | FHSS / DSSS |
| 802.11b | 1999 | 2,4 GHz | 11 Mbps | DSSS (CCK) |
| 802.11a | 1999 | 5 GHz | 54 Mbps | OFDM |
| 802.11g | 2003 | 2,4 GHz | 54 Mbps | OFDM (compatibil cu b) |
| 802.11n | 2009 | 2,4 + 5 GHz | 600 Mbps | OFDM + MIMO |
| 802.11ac | 2013 | 5 GHz | 1,3 Gbps | OFDM, channels mai late |

- Proiectul cere **cele 13 channels din 2,4 GHz**, adică rețele b/g/n (5 GHz e opțional).
- Rate-urile pe care le poți vedea în Radiotap:
  - 802.11b: **1, 2, 5,5, 11 Mbps**
  - 802.11a/g: **6, 9, 12, 18, 24, 36, 48, 54 Mbps**
  
  Tabelul 802.11a de la p. 52 arată de unde vin: modulation × coding rate × 48 data subcarriers ÷ 4 µs.
- **OFDM:** channel-ul e împărțit în **52 subcarriers** (48 data + 4 pilots), la 0,3125 MHz unul de altul, deci un semnal lat de **aproximativ 20 MHz**.

---

## 5. Cele 13 channels din 2,4 GHz (p. 52, Fig. 6 + p. 61)

**La ce folosește:** **channel graph-ul** și conversia channel ↔ frequency. **Foarte important.**

- **Formula:** `frequency (MHz) = 2407 + 5 · channel`, pentru channel 1–13. Channel 14 = 2484 MHz (doar Japonia).
- Invers, din frequency-ul din Radiotap: `channel = (frequency − 2407) / 5`.
- Pentru 5 GHz (opțional): `frequency = 5000 + 5 · channel` (ex. channel 36 = 5180 MHz).

| Ch | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| MHz | 2412 | 2417 | 2422 | 2427 | 2432 | 2437 | 2442 | 2447 | 2452 | 2457 | 2462 | 2467 | 2472 |

- Channels sunt la **5 MHz** distanță, dar un semnal ocupă **aproximativ 20–22 MHz**. Deci **fiecare rețea se suprapune cu cam ±2 channels vecine**.
  - Notițele scriu „5 MHz channel bandwidth” la p. 61: e de fapt **spacing-ul**, nu lățimea.
- Channels care nu se suprapun: **1 / 6 / 11** (sau **1 / 5 / 9 / 13** în Europa).
- **Pentru channel graph:** desenează fiecare AP ca un „deal” (curbă) centrat pe channel-ul lui, lat cât ±2 channels, cu înălțimea = signal strength (dBm). Unde se suprapun multe dealuri, channel-ul e aglomerat. Asta cere proiectul („how congested the 13 channels are”).
- Channel-ul unei rețele îl iei din **beacon** (information element 3, DS Parameter Set) sau, ca fallback, din frequency-ul din Radiotap.

---

## 6. Familia IEEE 802 și LLC (p. 53–55)

**La ce folosește:** **ordinea în care parsezi header-ele.** **Foarte important.**

- Data Link layer = **LLC** (802.2, **comun** tuturor rețelelor 802) + **MAC** (diferit: 802.3 Ethernet, 802.11 Wi-Fi etc.).
- **LLC multiplexează** protocoalele de deasupra (IP, ARP…). În practică asta înseamnă câmpul **EtherType** din SNAP header:
  - `0x0800` = IPv4 (ce parsezi)
  - `0x0806` = ARP
  - `0x86DD` = IPv6
  - `0x888E` = EAPOL (WPA handshake)
- **Cele două lanțuri de parsare:**
```
Monitor mode:  Radiotap → 802.11 MAC header → LLC/SNAP (AA AA 03 00 00 00 + EtherType) → IPv4 → TCP → HTTP
Managed mode:  Ethernet header (dst MAC 6, src MAC 6, EtherType 2)                        → IPv4 → TCP → HTTP
```
  Ambele ajung la **EtherType 0x0800**, iar de acolo același cod IPv4/TCP/HTTP merge pentru ambele moduri.
- **Bus topology** (Ethernet vechi): toate stațiile aud toate frame-urile și le aruncă pe cele care nu le sunt adresate. **Wi-Fi e la fel, un mediu partajat.** **Monitor mode** înseamnă „nu mai arunca nimic”, iar asta e baza sniffer-ului.
- Token ring (802.4/802.5) nu are legătură cu proiectul. Notițele greșesc: 802.4 e token *bus*, nu ring.

---

## 7. Stack-ul 802.11 și serviciile MAC (p. 55–56)

**La ce folosește:** ce conține MAC header-ul și o capcană la parsare.

- Stack-ul: **Network (IP)** → **LLC** → **MAC** → **PHY** (FHSS / DSSS / IR / OFDM).
- **Serviciile MAC:**
  - acces la mediu (**CSMA/CA**)
  - **addressing** (MAC addresses), pe care le extragi
  - delimitarea frame-urilor
  - **FCS** (Frame Check Sequence): un **CRC de 4 bytes la sfârșitul frame-ului**
- ⚠️ **Capcana FCS:** multe drivere lasă FCS-ul în frame-ul capturat. **Radiotap Flags bit `0x10`** îți spune dacă e prezent. Dacă e setat, **taie ultimii 4 bytes** înainte să parsezi body-ul, altfel payload-ul HTTP se termină cu 4 bytes de gunoi.
  - **Radiotap Flags bit `0x40`** = bad FCS: frame corupt. Poți să-l ignori sau să-l marchezi.

---

## 8. Physical layer: PLCP, FHSS, DSSS (p. 56–64)

**La ce folosește:** aproape deloc pentru cod, pentru că totul se întâmplă în hardware. Reține doar ce apare în Radiotap.

- PHY = **PLCP** (adaugă preamble + header în fața MAC frame-ului) + **PMD** (modulează și transmite).
- **PLCP-ul este eliminat de placă.** libpcap nu ți-l dă; bytes-ii tăi încep cu **Radiotap header-ul**.
- **Spread spectrum** (FHSS, DSSS, OFDM) și modulation-urile (GFSK, DBPSK, DQPSK) sunt teorie pentru examen, nu pentru proiect.
  - „Frequency hopping” din FHSS **nu** e același lucru cu **channel hopper**-ul din proiectul nostru. Al nostru doar schimbă channel-ul plăcii în monitor mode ca să asculte pe rând toate cele 13 channels.
- **Urmele PHY-ului pe care le vezi în Radiotap:**

| Concept din curs | Câmp Radiotap | Detalii |
|---|---|---|
| Viteza (câmpul Signal din PLCP) | **Rate** | unități de 500 kbps: 2 = 1 Mbps, 22 = 11 Mbps, 108 = 54 Mbps |
| Long vs short preamble (144 µs vs ~72 µs) | **Flags** bit `0x02` | 1 = short preamble |
| DSSS/CCK (802.11b) vs OFDM (802.11g) | **Channel** flags | `0x0020` = CCK, `0x0040` = OFDM, `0x0080` = 2 GHz, `0x0100` = 5 GHz |
| Frequency-ul channel-ului | **Channel** frequency | MHz (ex. 2437 = channel 6) |
| Signal strength | **dBm Antenna Signal** | byte signed, ex. −58 |

- **Obligatoriu pentru proiect:** signal strength (dBm) și frequency. Rate, preamble și CCK/OFDM sunt opționale („nice to have” în UI).
- **Model mental util:** tabelele PLCP (câmpuri de lungime fixă, un câmp **Length**, un **CRC**) arată exact cum vei parsa orice header: citești câmpuri la offset-uri cunoscute, iar un câmp de lungime îți spune unde începe layer-ul următor.

---

## 9. MAC: accesul la mediu, CSMA/CA (p. 65)

**La ce folosește:** ca să înțelegi de ce o captură e plină de frame-uri mici (ACK, RTS, CTS) și ce înseamnă câmpul **Duration**.

- **Problema:** mai multe stații împart același mediu (aerul). Cine transmite și când?
  - GSM și token ring **rezervă** resurse (timp sau frecvență) pentru fiecare stație.
  - Ethernet și Wi-Fi îi lasă pe toți să încerce și rezolvă conflictele când apar.
- **CSMA/CD** (Ethernet): transmiți, detectezi coliziunea, te oprești și aștepți un timp random.
- **CSMA/CA** (Wi-Fi):
  1. asculți mediul (carrier sense);
  2. dacă e ocupat, aștepți un **backoff random**;
  3. dacă e liber cel puțin **DIFS**, transmiți.
- **Interframe spaces:**
  - **SIFS** (Short IFS, aproximativ 10 µs): cel mai scurt, pentru răspunsuri prioritare (ACK, CTS).
  - **DIFS** (DCF IFS): mai lung, pentru transmisii normale.
  - Pentru că SIFS < DIFS, răspunsurile au mereu prioritate.
- **Positive acknowledgement:** fiecare unicast frame primește un **ACK**. Dacă ACK-ul nu vine, frame-ul se retransmite, cu **Retry bit = 1**.
- **RTS/CTS** (opțional, util când e aglomerat):
  1. stația trimite **RTS** (Request To Send);
  2. **receiver-ul** (de obicei AP-ul) răspunde cu **CTS** (Clear To Send);
  3. abia apoi vin datele.
- **Câmpul Duration** din fiecare frame spune celorlalte stații cât timp (µs) e rezervat mediul. Ele își setează **NAV** (Network Allocation Vector) și tac în acest timp.

⚠️ **Trei greșeli în notițe:**
- Wi-Fi **nu poate detecta coliziuni** în timp ce transmite: radio-ul nu poate asculta și transmite simultan. Coliziunea se deduce din **lipsa ACK-ului**, de aceea se cheamă *Collision Avoidance*.
- La RTS/CTS **nu răspund toate stațiile** cu CTS, ci doar receiver-ul. Celelalte aud CTS-ul și tac (prin NAV).
- SIFS se măsoară în **microsecunde**, nu milisecunde.

**În captură:** ACK, RTS și CTS sunt foarte frecvente și nu conțin date utile. Sniffer-ul ar trebui să aibă un **filtru** care ascunde control frames.

---

## 10. 802.11 MAC frame (p. 66–67, Fig. 29–30). **CEL MAI IMPORTANT PENTRU COD**

**La ce folosește:** e header-ul pe care îl parsezi byte cu byte în monitor mode (după Radiotap). De aici iei **source/destination MAC** (cerință obligatorie) și **tipul frame-ului** (beacon pentru analyzer, data pentru sniffer).

### Layout-ul general
```
| Frame Control | Duration/ID | Addr1 | Addr2 | Addr3 | Seq Control | Addr4 | QoS Ctrl | HT Ctrl | Frame Body | FCS |
|      2        |      2      |   6   |   6   |   6   |      2      |  (6)  |   (2)    |   (4)   |  0–2312    |  4  |
```
- Header-ul de bază (FC → Seq Control) are **24 bytes**. Câmpurile în paranteză apar doar uneori:
  - **Addr4** (6): doar dacă **To DS = 1 și From DS = 1** (wireless bridge/mesh).
  - **QoS Control** (2): doar la **QoS Data** frames (subtype 8–15), adică majoritatea datelor moderne (802.11n/ac).
  - **HT Control** (4): doar dacă e setat bitul **Order** (rar).
- **Toate câmpurile numerice 802.11 sunt little-endian** (Duration, Sequence Control).
- **FCS** (4 bytes) e la final doar dacă Radiotap Flags are bitul `0x10` (vezi secțiunea 7).

### Frame Control (2 bytes): primul lucru pe care îl citești
Primul byte (`fc0`) conține version, type și subtype. Al doilea (`fc1`) conține flags.
```
fc0:  bit 7 6 5 4 | 3 2 | 1 0          fc1: bit 7     6         5        4        3     2         1         0
      subtype     | type| version           Order Protected MoreData PwrMgmt Retry MoreFrag  FromDS   ToDS
```
```java
int fc0 = data[off] & 0xFF;          // & 0xFF: byte e signed în Java!
int fc1 = data[off + 1] & 0xFF;
int version = fc0 & 0x03;            // mereu 0
int type    = (fc0 >> 2) & 0x03;     // 0 = management, 1 = control, 2 = data
int subtype = (fc0 >> 4) & 0x0F;
boolean toDS      = (fc1 & 0x01) != 0;
boolean fromDS    = (fc1 & 0x02) != 0;
boolean retry     = (fc1 & 0x08) != 0;
boolean protectedFrame = (fc1 & 0x40) != 0;   // criptat (WEP/WPA/WPA2)
```

### Type / subtype: cele care contează

| Type | Subtype | Nume | Byte `fc0` | Pentru proiect |
|---|---|---|---|---|
| 0 mgmt | 8 (1000) | **Beacon** | `0x80` | ⭐ **Wi-Fi Analyzer**: SSID, channel, security, signal |
| 0 mgmt | 5 (0101) | **Probe Response** | `0x50` | ⭐ ca beacon-ul (răspuns la scanare) |
| 0 mgmt | 4 (0100) | Probe Request | `0x40` | clienți care caută rețele |
| 0 mgmt | 0 / 1 | Association Req / Resp | `0x00` / `0x10` | conectare la AP |
| 0 mgmt | 11 (1011) | Authentication | `0xB0` | |
| 0 mgmt | 10 / 12 | Disassociation / Deauthentication | `0xA0` / `0xC0` | |
| 1 ctrl | 13 (1101) | **ACK** | `0xD4` | zgomot, de filtrat |
| 1 ctrl | 11 / 12 | **RTS** / **CTS** | `0xB4` / `0xC4` | zgomot, de filtrat |
| 2 data | 0 (0000) | **Data** | `0x08` | ⭐ **sniffer**: conține IP |
| 2 data | 8 (1000) | **QoS Data** | `0x88` | ⭐ **sniffer**: cel mai frecvent; are +2 bytes QoS Control |
| 2 data | 4 / 12 | Null / QoS Null | `0x48` / `0xC8` | **fără body** (power save), nu căuta IP în ele |

⚠️ **Control frames sunt scurte:** ACK și CTS au doar FC + Duration + **Addr1** (10 bytes), RTS are FC + Duration + Addr1 + Addr2 (16 bytes). **Nu citi Addr3 sau Seq Control la control frames**, altfel ieși din array (`ArrayIndexOutOfBoundsException`). Verifică mereu lungimea înainte să citești.

### Cele 4 adrese: semnificația depinde de To DS / From DS
Notițele doar le listează. Tabelul de mai jos e cel pe care îl folosești în cod:

| To DS | From DS | Addr1 | Addr2 | Addr3 | Addr4 | Când |
|---|---|---|---|---|---|---|
| 0 | 0 | **Destination** | **Source** | BSSID | – | management frames (beacons), ad-hoc |
| 0 | 1 | **Destination** | BSSID | **Source** | – | AP → client (download) |
| 1 | 0 | BSSID | **Source** | **Destination** | – | client → AP (upload) |
| 1 | 1 | Receiver | Transmitter | **Destination** | **Source** | wireless bridge / mesh |

- La **beacon**: Addr1 = `ff:ff:ff:ff:ff:ff` (broadcast), Addr2 = Addr3 = **BSSID**.
- **Receiver/Transmitter** = stațiile radio care trimit și primesc fizic frame-ul în acest „hop”. **Source/Destination** = capetele reale ale conversației. Cerința „source and destination MAC” înseamnă coloanele îngroșate.
- **Formatul afișat:** fiecare byte în hex, separat prin `:`, de ex. `a4:2b:b0:12:34:56`. Primii 3 bytes sunt **OUI**-ul, din care obții **manufacturer**-ul.

### Celelalte câmpuri
- **Duration/ID** (2 bytes, LE): microsecunde rezervate (NAV), vezi secțiunea 9.
- **Sequence Control** (2 bytes, LE): **fragment number** = cei 4 biți de jos, **sequence number** = cei 12 biți de sus. Util la afișare și ca să recunoști retransmisiile (același seq number + Retry = 1).
- **Frame Body:**
  - la management frames: câmpurile fixe + information elements (vezi `course-notes-summary.md`, secțiunea 3);
  - la data frames: **LLC/SNAP + pachetul IP** (secțiunea 12);
  - dacă **Protected = 1**: body-ul e **criptat** și nu poți parsa IP-ul. Afișează „encrypted (WPA2)”.
- **FCS** (4 bytes): CRC32. Nu e nevoie să-l verifici (Radiotap îți spune prin bitul `0x40` dacă e greșit).

### Corecții / completări față de notițe
- „Type=01 subtype 1010 (power save)” e **PS-Poll**.
- Lipsesc din notițe **QoS Data** (cel mai frecvent data frame), **Null data** și **Deauthentication**.

---

## 11. Ethernet (802.3) frame (p. 67, Fig. 31)

**La ce folosește:** în **managed mode** placa îți dă frame-urile **convertite în Ethernet**, deci acesta e header-ul pe care îl parsezi acolo (link type `DLT_EN10MB` = 1).

```
| Destination MAC | Source MAC | EtherType | Payload (IP packet) |
|        6        |     6      |     2     |      46–1500        |
```
- Doar **2 adrese** (destination **primul**, apoi source), fără Frame Control și fără To DS / From DS. Header-ul are fix **14 bytes**.
- **EtherType** e **big-endian** (network order): `0x0800` = IPv4, `0x0806` = ARP, `0x86DD` = IPv6.
  - Dacă valoarea e ≤ 1500, e de fapt un câmp de *length* (802.3 cu LLC). Rar; îl poți afișa ca „unsupported”.
- **Preamble/SFD și FCS** din figură **nu apar** în captură, pentru că le elimină placa sau driver-ul.
- În UI scrie clar „Ethernet header (managed mode)”, nu „802.11 header”. Kernel-ul a scos header-ul 802.11 real.

---

## 12. LLC (802.2) și încapsularea IP în 802.11 (p. 67–68, Fig. 32–33)

**La ce folosește:** e puntea dintre 802.11 MAC header și pachetul IP în **monitor mode**.

- LLC e **comun** tuturor rețelelor 802. Oferă trei tipuri de servicii:
  - **unacknowledged connectionless:** fără control de erori; asta se folosește în practică, iar TCP se ocupă de fiabilitate;
  - **connection-oriented;**
  - **acknowledged connectionless.**
- **Încapsularea** (RFC 1042, „Ethernet tunnel”), adică primii **8 bytes** din body-ul unui data frame necriptat:
```
| DSAP | SSAP | Control |   OUI (SNAP)   | EtherType | IP packet ...
| 0xAA | 0xAA |  0x03   | 0x00 0x00 0x00 | 0x08 0x00 |
|<---- LLC header ----->|<------ SNAP header ------->|
```
- **Verificarea în cod:** dacă body-ul începe cu `AA AA 03`, citește EtherType-ul de la offset +6 (**big-endian**). Dacă e `0x0800`, IP-ul începe la offset +8.
- Atenție la byte order: header-ul 802.11 e **little-endian**, iar EtherType-ul din SNAP și tot ce urmează (IP, TCP) sunt **big-endian**.
- **DSAP/SSAP** (Destination/Source Service Access Point) identifică protocolul de deasupra. `0xAA` înseamnă „urmează un SNAP header”, iar protocolul real e dat de EtherType.
- Notițele spun „Subnetwork Access Protocol (SAP)”. Corect: **SAP = Service Access Point**, iar **SNAP = Subnetwork Access Protocol**.

---

## 13. Avantaje și probleme WLAN (p. 68–69)

**La ce folosește:** argumente pentru prezentarea proiectului.

- **Avantaje:** mobilitate, instalare unde nu poți trage cabluri, cost mic de întreținere, viteză în creștere.
- **Probleme:**
  - viteza reală e sub Ethernet;
  - **interference** (banda ISM e partajată cu microunde și Bluetooth);
  - **security** (mediu partajat, oricine poate asculta, exact ce face sniffer-ul tău);
  - roaming fără standard comun.
- **Probleme de implementare:** AP-uri plasate „după ureche” (*best guess*) și **„unaware of interference from/to other networks”**.
- 💡 **Argument pentru prezentare:** Wi-Fi Analyzer-ul tău rezolvă exact problema asta. Arată ce rețele sunt în jur, pe ce channels și cât de puternic, ca să alegi **channel-ul cel mai puțin aglomerat**. Iar sniffer-ul demonstrează problema de securitate: într-o rețea deschisă, traficul HTTP se citește în clar.

---

## 14. Wireless security, sniffing și WEP (p. 69–73)

**La ce folosește:** ce e **monitor mode** (cerința de bază), comenzile Linux pentru el, un instrument de **validare** pentru analyzer-ul tău și cum recunoști **WEP** în coloana de security.

### Securitate în general
- Wi-Fi ajunge la 25–125 m (notițele scriu „2.5 GHz”; corect e **2,4 GHz**), deci oricine din raza asta poate capta traficul.
- **MAC filtering** pe AP nu e sigur: îți poți schimba MAC-ul (**MAC spoofing**) ca să pari un client „acceptat”.
  - 💡 **Pentru proiect:** telefoanele moderne folosesc **MAC-uri randomizate**, mai ales în probe requests. Le recunoști după bitul **locally administered**: `(primul byte & 0x02) != 0`. Pentru ele, lookup-ul OUI nu găsește manufacturer, așa că afișează „randomized” în loc de „unknown”.
- **IPSec** între client și AP ar fi o soluție, dar e prea complicat pentru utilizatori.

### Promiscuous vs monitor mode ⭐
| Mod | Ce vezi | Link type în libpcap |
|---|---|---|
| **Managed** (normal) | doar traficul tău + broadcast/multicast din rețeaua ta | Ethernet (`DLT_EN10MB`) |
| **Managed + promiscuous** | teoretic tot traficul din rețeaua la care ești conectat; **în practică, pe Wi-Fi, majoritatea driverelor tot doar traficul tău** | Ethernet |
| **Monitor** | **toate frame-urile din raza radio**, de la orice rețea, inclusiv beacons, fără să fii conectat | Radiotap + 802.11 (`DLT_IEEE802_11_RADIO`) |

- **Consecință pentru sniffer:** în **managed mode** vei vedea în principal **traficul laptopului tău**. Pentru demo e suficient: deschizi un site HTTP de pe laptop și îl vezi în sniffer. În **monitor mode** vezi și traficul altor dispozitive, dar doar dacă rețeaua e **deschisă** (altfel e criptat).

### Comenzile din curs și echivalentele moderne
Notițele folosesc comenzi vechi. `iwconfig`, `iwlist` și `ifconfig` sunt **deprecated**; pe Ubuntu 24.04 se folosesc `iw` și `ip`.

| Din curs | Ce face | Modern / ce folosim noi |
|---|---|---|
| `iwconfig`, `iwlist` | info și control interfață wireless | `iw dev`, `iw list`, `iw dev <if> info` |
| `ifconfig` | IP-uri, up/down | `ip addr`, `ip link set <if> up/down` |
| `airmon-ng` | listează interfețele | `iw dev` |
| `airmon-ng start wlp2s0` | pornește monitor mode (creează `wlp2s0mon`; în versiuni vechi `mon0`) | `sudo iw dev <if> set type monitor` (după `ip link set <if> down`) |
| `airmon-ng check kill` | **oprește NetworkManager și wpa_supplicant**, care altfel schimbă interfața înapoi în managed | `sudo nmcli dev set <if> managed no` (mai blând: oprește doar pentru interfața aceea) |
| `airodump-ng mon0` | listează rețelele (BSSID, PWR, CH, ENC, ESSID) | ⭐ **exact ce face Wi-Fi Analyzer-ul tău** |

⚠️ `airmon-ng check kill` **îți taie internetul**. Ca să-l repornești: `sudo systemctl restart NetworkManager` (sau reboot).

### Kismet și airodump-ng: instrumente de validare 💡
- **Kismet** = wireless monitoring / wardriving tool: găsește rețele, inclusiv **hidden SSIDs** („unadvertised”), asociază clienți cu AP-uri și detectează comportament suspect.
- **airodump-ng** afișează pentru fiecare rețea: **BSSID, PWR (dBm), Beacons, CH, ENC/CIPHER/AUTH, ESSID**. Sunt aproape exact câmpurile cerute de proiect.
- **Folosește-le ca să-ți testezi aplicația:** rulezi `airodump-ng` și analyzer-ul tău unul lângă altul (cu `wireshark` pentru pachete). Dacă SSID-ul, BSSID-ul, channel-ul, dBm-ul și security-ul coincid, parserele tale sunt corecte.
- Pentru instalare pe laptop: `sudo apt install aircrack-ng kismet`.

### Atacuri pe WPA2-PSK (aircrack-ng): ⛔ în afara proiectului
- Pași: monitor mode, capturezi **4-way handshake**-ul unui client, rulezi un **dictionary attack** offline (`aircrack-ng -w rockyou.txt`). Pentru a grăbi, **deauth** (`aireplay-ng -0`) forțează clientul să se reconecteze.
- **Nu e cerut de proiect** și **nu-l rula pe rețele care nu sunt ale tale**: e infracțiune (acces ilegal / interceptare ilegală în Codul Penal), iar deauth-ul perturbă activ rețeaua altora.
- **Legătura utilă:** handshake-ul WPA e transportat în frame-uri **EAPOL**, cu **EtherType `0x888E`** în SNAP header. Sniffer-ul le va vedea în monitor mode și le poți eticheta „EAPOL (WPA handshake)”. Opțional, dar arată bine la demo.
- Interesant: notițele spun că **airdecap-ng** poate decripta trafic WPA dacă știi parola. Decriptarea în aplicația ta ar fi mult peste cerințe; nu o face.

### WEP (Wired Equivalent Privacy)
- Folosește **RC4**, un **stream cipher**: dintr-o cheie scurtă generează un **keystream** care se face XOR cu mesajul. `ciphertext = plaintext ⊕ keystream`, iar `plaintext = ciphertext ⊕ keystream`.
  - Dacă știi două din trei (plaintext, ciphertext, keystream), îl afli pe al treilea, deci **refolosirea keystream-ului e fatală**.
- Seed-ul RC4 = **cheia secretă + un IV de 24 de biți**, trimis **în clar** în fiecare frame.
  - 24 de biți e prea puțin: probabilitate de 99% ca un IV să se repete după doar **~12.430 frames** (1–2 secunde la 11 Mbps).
- Alte probleme: cheia nu se schimbă automat, RC4 are **weak keys**, iar WEP nu le evită.
- **Atacuri:**
  - *active*: injectezi plaintext cunoscut (ex. un email) și obții keystream-ul;
  - *passive*: header-ele IP sunt previzibile, deci recuperezi bucăți din keystream; XOR între două ciphertexts cu același IV dă XOR-ul plaintext-urilor.
- **Concluzie:** WEP e spart complet (Airsnort, WEPCrack, aircrack-ng).

**Pentru coloana security din analyzer:**
| Ce găsești în beacon | Afișezi |
|---|---|
| Privacy bit = 0 în Capability Info | **Open** |
| Privacy bit = 1, **fără** RSN IE (48) și fără WPA IE (221) | **WEP** |
| WPA IE (221, OUI `00:50:F2` type 1) | **WPA** |
| RSN IE (48) cu AKM = PSK (2) | **WPA2-Personal** |
| RSN IE cu AKM = 802.1X (1) | **WPA2-Enterprise** |
| RSN IE cu AKM = SAE (8) | **WPA3-Personal** (dacă are și PSK: „WPA2/WPA3”) |

(Detaliile despre Capability Info și RSN IE nu sunt în notițe; le adăugăm când ajungi la parserul de beacon.)

### WPA / WPA2
- **Secțiunea din notițe e goală** (doar titlul). Surse în `docs/course-notes-summary.md`, secțiunea 3.

---

## 15. Instrumentele din curs: ce folosim și ce nu

**Concluzie:** din suita aircrack-ng nu intră **nimic în cod**. Păstrăm doar **airodump-ng și Wireshark, ca referință manuală de test**.

| Din curs | Ce face | Îl folosim? |
|---|---|---|
| `aircrack-ng` + `rockyou.txt` | sparge parola WPA2 (dictionary attack) | ❌ **Nu.** Nu e cerut; ilegal pe rețele străine |
| `aireplay-ng -0` | **deauth**: deconectează forțat clienți (packet injection) | ❌ **Nu.** Nu e cerut; perturbă activ rețeaua altora, ilegal pe rețele străine. Proiectul e doar **pasiv** |
| `airdecap-ng` | decriptează trafic WEP/WPA cu parola cunoscută | ❌ **Nu**, mult peste cerințe |
| `airmon-ng start` / `check kill` | pune placa în monitor mode | ❌ **Nu în aplicație** (vezi mai jos) |
| `airodump-ng` | listează rețelele: BSSID, PWR, CH, ENC, ESSID | ⚠️ **Doar ca referință de test**, manual, comparat cu analyzer-ul nostru |
| Kismet | wireless monitoring | ⚠️ Opțional, tot pentru validare. Nu e necesar |
| `iwconfig`, `iwlist`, `ifconfig` | control interfață | ❌ Deprecated. Folosim `iw`, `ip`, `nmcli` |
| Huffman/LZW, `podman`/`docker`, `oc` | alte capitole | ❌ Nicio legătură |

### De ce nu `airmon-ng`, deși face monitor mode
- **Pe laptop nu ajunge:** RTL8822CE are nevoie de reload la driver (`modprobe -r rtw88_8822ce && modprobe rtw88_8822ce`) înainte de monitor mode, altfel 2.4 GHz nu primește nimic. `airmon-ng` nu face reload.
- `airmon-ng check kill` **oprește tot NetworkManager-ul** și taie internetul. Noi folosim `nmcli dev set <if> managed no`, care afectează doar interfața Wi-Fi.
- `airmon-ng start` poate crea o interfață nouă (`wlp4s0mon`), ceea ce complică codul.
- În spate tot `iw` + `ip` face treaba, exact ce rulează deja `InterfaceService` și `ChannelHopper`.

### ⚠️ Regula importantă pentru notă
Aplicația **nu are voie să cheme `airodump-ng`, `tcpdump`, `tshark`** sau alte tool-uri ca să obțină datele și să le parseze output-ul. Atunci munca ar fi a lor, iar cerința spune explicit că parsarea trebuie făcută manual.

**Permis:**
- `iw` / `ip` / `nmcli` / `modprobe`, pentru schimbarea modului și a channel-ului: funcții de OS cu operații minimale, cum permite cerința;
- **libpcap prin FFM**, care doar deschide captura și dă **bytes bruți**. Tot restul (Radiotap, 802.11, LLC/SNAP, IPv4, TCP, HTTP) îl parsăm noi.

### Cum folosești instrumentele de validare
- `sudo apt install aircrack-ng` (Wireshark e deja instalat).
- Rulezi `airodump-ng` (sau Wireshark pe aceeași interfață) **în paralel** cu aplicația ta. Dacă SSID, BSSID, channel, dBm și security coincid, parserele sunt corecte.
- Testezi doar pe rețeaua sau hotspot-ul tău. Rețelele vecine le poți **vedea** (beacons sunt publice), dar nu intervii asupra lor.

---

## 16. Ce urmează
- Notițele de curs relevante pentru proiect se termină aici. Restul PDF-ului (SDN, HPC, containere, Kubernetes) nu are legătură.
- **Nu sunt în notițe** (vezi `docs/course-notes-summary.md`, secțiunea 3): **Radiotap**, **information elements** din beacon (SSID, channel, RSN), **WPA2/WPA3**, header-ele **IPv4/TCP**, **libpcap**. Le adăugăm aici pe măsură ce ajungi la ele.
