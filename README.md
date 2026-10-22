# UABEA Mobile

Editor **AssetBundle Unity** untuk Android, ditulis ulang dalam Java sebagai
port ringan dari [UABEA](https://github.com/nesrak1/UABEA) (versi desktop
C#/Avalonia). Aplikasi ini bisa dibuild langsung di Termux (aarch64) tanpa
Android Studio, tanpa .NET, dan tanpa NDK.

- Paket: `com.uabea.mobile`
- Ukuran APK debug: ± 280 KB
- Minimum Android: 5.0 (API 21), target API 34
- Dependensi eksternal: hanya `org.tukaani:xz:1.9` (dekompresi LZMA)

---

## Fitur

| Fitur | Status |
|---|---|
| Buka `.bundle` / `.ab` / `.assets` / `SerializedFile` mandiri | ✅ |
| Tampilan ala UABEA: menu File/Tampilan/Alat/Bantuan, toolbar aksi, tabel aset | ✅ |
| Pilih berkas data (CAB) di dalam bundle lewat combo box | ✅ |
| Daftar aset: ikon tipe, nama, container/path ID, tipe, ukuran | ✅ |
| Urutkan berdasarkan nama / tipe / ukuran (klik header tabel) | ✅ |
| Pencarian nama / tipe / container + filter tipe | ✅ |
| Ekstrak **TextAsset** (`.txt`) | ✅ |
| Ekstrak **Texture2D** (`.png`) | ✅ (lihat dukungan format) |
| Ekstrak **AudioClip** (`.wav`, `.mp3`, `.ogg`, `.fsb`) | ✅ |
| **Ekspor semua** aset ke folder (SAF) | ✅ |
| Ganti isi **TextAsset** (file apa pun) | ✅ |
| Ganti isi **Texture2D** (PNG/JPG → RGBA32) | ✅ |
| **Impor semua** TextAsset/Texture2D dari folder (cocokkan nama) | ✅ |
| **Ganti nama** aset (field `m_Name`) | ✅ |
| **Lihat data** objek berdasarkan TypeTree | ✅ |
| **Pohon tipe** (TypeTree viewer) | ✅ |
| Simpan / repack bundle (timpa atau file baru) | ✅ |
| Simpan file `.assets` mandiri | ✅ |
| Pratinjau teks, gambar, dan putar audio | ✅ |
| Jendela **Info Aset** ala UABEA (properti + aksi + pratinjau) | ✅ |
| Tema terang / gelap (mengikuti sistem, bisa diganti dari menu Tampilan) | ✅ |
| Semua teks UI berbahasa Indonesia | ✅ |

### Format bundle

* **UnityFS** dengan blok tanpa kompresi, **LZ4**, atau **LZMA**.
* SerializedFile format 6–22 (Unity 5.x – 2022+), header big-endian maupun
  little-endian, TypeTree inline.
* Offset objek yang relatif terhadap `dataOffset` (hasil repacker lain)
  dideteksi dan dinormalkan otomatis.
* Repack menulis header `UnityFS` versi asli dengan blok LZ4 dan blocksInfo
  di awal file (`flags = 0x42`).

### Format tekstur

Didekode langsung di Java (tanpa GPU):

`Alpha8`, `ARGB4444`, `RGB24`, `RGBA32`, `ARGB32`, `RGB565`, `R16`,
`RGBA4444`, `BGRA32`, `R8`, `DXT1`, `DXT3`, `DXT5`, `BC4`, `BC5`,
`ETC1`, `ETC2_RGB`, `ETC2_RGBA1`, `ETC2_RGBA8` (termasuk mode T/H/planar
dan alpha EAC).

Didekode lewat GPU (EGL + shader, otomatis bila Java tidak mendukung):

`ASTC 4x4–12x12`, `BC6H`, `BC7`, `PVRTC 2bpp/4bpp`.

---

## Batasan yang diketahui

1. **Bundle terenkripsi** (UnityCN, flag enkripsi) dan format **UnityWeb**
   belum didukung; aplikasi menampilkan pesan bahwa format belum didukung.
2. **Tekstur Crunch** (`DXT1/5 Crunched`, format 28/29) dan format tekstur
   lain yang tidak dikenali tidak bisa dipratinjau; data mentahnya tetap bisa
   diekspor sebagai `.tex`.
3. **Audio Vorbis** di dalam FSB5 diekspor sebagai `.fsb` mentah karena
   pembungkusan ulang ke Ogg butuh encoder Vorbis. PCM → WAV dan MPEG → MP3
   berjalan normal.
4. Penggantian isi hanya untuk **TextAsset** dan **Texture2D**. Tipe lain
   hanya bisa dilihat dan diekspor mentah.
5. Bundle hasil repack memakai kompresi **LZ4**, sehingga ukurannya bisa lebih
   besar daripada asli bila bundle asli memakai LZMA.
6. Tekstur yang diganti selalu dikonversi ke **RGBA32** dan mipmap dihapus.
   Gambar impor dibatasi 16 megapiksel agar tidak kehabisan memori.
7. File dibaca sepenuhnya ke memori (batas 512 MB). Bundle raksasa bisa
   menyebabkan kehabisan memori pada HP RAM kecil.
8. Pratinjau **ASTC/BC6H/BC7/PVRTC** bergantung pada dukungan GPU. Di
   perangkat yang tidak mendukung formatnya, tekstur tetap bisa diekspor
   mentah. DXT/BC4/BC5/ETC1/ETC2 selalu didekode di Java tanpa GPU.
9. **Hapus aset** dan **sistem plugin** belum diimplementasikan; tombolnya
   tetap ditampilkan agar susunan UI sama dengan UABEA, tetapi akan
   menampilkan pesan bahwa fitur belum tersedia.
10. **Ganti nama** bekerja pada objek yang memiliki field `m_Name` di
    TypeTree. Nama di container/path AssetBundle tidak ikut diubah.

---

## Build di Termux

Prasyarat (sekali saja):

```bash
pkg update
pkg install openjdk-17 gradle unzip curl aapt2
# Android SDK: platform android-34 + build-tools 34.0.0
# (folder default: $HOME/android-sdk, lihat build.sh)
```

Build:

```bash
cd ~/uabea-android
./build.sh
```

Hasil:

```
output/app-debug.apk
```

Pasang di HP:

```bash
termux-open output/app-debug.apk
```

atau buka file `output/app-debug.apk` dari aplikasi file manager.

### Catatan build

* `build.sh` otomatis meneruskan `-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)`
  di aarch64, karena `aapt2` bawaan Google Maven berarsitektur x86_64.
* `gradlew` akan memakai Gradle 8.1.1 di `$HOME/.gradle/toolbox-dist`, atau
  mengunduhnya bila belum ada.
* `org.gradle.jvmargs=-Xmx1024m` disetel agar aman di HP RAM terbatas.
* Build debug ditandatangani dengan debug keystore Gradle, cukup untuk
  pemakaian sendiri (bukan untuk Play Store).

---

## Cara pakai

1. **File > Buka…**, lalu pilih file bundle (mis. `character.ab`).
2. Pilih berkas data di combo **Berkas data** bila bundle punya beberapa CAB.
   Tabel aset bisa diurutkan dengan mengetuk header **Nama / Tipe / Ukuran**.
3. Ketuk aset sekali untuk memilih; ketuk lagi (atau tombol **Info**) untuk
   membuka jendela **Info Aset**. Di sana tersedia pratinjau, properti,
   **Lihat Data**, **Ekspor**, **Impor**, dan **Ganti Nama**.
4. **Ekspor** menyimpan satu aset; **Ekspor Semua** memilih folder lalu
   menulis semua aset dari berkas data yang aktif. **Impor Semua** membaca
   folder dan mencocokkan nama file dengan nama aset TextAsset/Texture2D.
5. Untuk mengganti isi satu aset, pilih aset lalu tekan **Impor**:
   * TextAsset: pilih file teks/JSON/byte apa pun.
   * Texture2D: pilih gambar PNG/JPG (otomatis jadi RGBA32).
6. **File > Simpan** menimpa file asli (bila gagal, aplikasi menawarkan
   lokasi baru). **File > Simpan sebagai…** selalu memilih file baru.
7. **Tampilan > Tema gelap** mengganti tema tanpa menutup bundle. Menu
   **Tampilan** juga berisi pencarian, lanjut pencarian, dan filter tipe.
8. Tekan lama judul **UABEA Mobile** untuk melihat info dan batasan.

---

## Struktur proyek

```
app/src/main/java/com/uabea/mobile/
├── MainActivity.java        UI ala UABEA, menu, dialog info/data, alur simpan
├── Ui.java                  Palet tema terang/gelap + gaya kontrol
├── AssetAdapter.java        Adapter tabel aset
├── GpuTextureDecoder.java   Dekode ASTC/BC6H/BC7/PVRTC lewat EGL + GLES
├── Util.java                Format ukuran, nama file, pesan error
└── core/                    Kode murni Java (bisa diuji dengan javac)
    ├── Buf.java             Pembaca little/big-endian
    ├── Out.java             Penulis + patch
    ├── Lz4.java             Block LZ4 encode/decode
    ├── Lzma.java            LZMA1 (tanpa EOS) via XZ
    ├── TypeNode.java        Node TypeTree
    ├── TypeTreeIO.java      Baca/tulis nilai generik berdasarkan TypeTree
    ├── SerializedFile.java  Parser + rebuild CAB
    ├── UnityBundle.java     Parser + repack UnityFS
    ├── Asset.java           Satu objek aset
    ├── AssetIndex.java      Indeks aset + container
    ├── AssetExtractor.java  Ekstraksi/penggantian Text/Texture/Audio + rename
    ├── DataDump.java        Dump nilai objek & pohon tipe (View Data)
    ├── TextureDecoder.java  Dekoder tekstur Java
    ├── Fsb5.java            Parser audio FMOD FSB5
    ├── Png.java             Encoder PNG minimal
    ├── CommonStrings.java   Tabel common string TypeTree
    └── ClassNames.java      Tabel classId Unity
```

Seluruh kode di `core/` tidak memakai API Android, sehingga bisa dikompilasi
dan diuji di desktop:

```bash
javac -encoding UTF-8 -d classes -cp xz-1.9.jar app/src/main/java/com/uabea/mobile/core/*.java
```

---

## Pengujian yang sudah dilakukan

Diuji dengan bundle nyata (JVM, bukan emulator):

| Sampel | Unity | Isi | Hasil |
|---|---|---|---|
| `yuki.ab` | 5.6 | 36 aset, 35 AudioClip FSB5 Vorbis | baca, repack, ekstrak ✅ |
| `xinzexi_2_n_tex` | 2017.4 | Texture2D ETC2_RGBA8 2048×976 stream | baca, repack, ganti tekstur ✅ |
| `banner_1` | 2018.4 | Texture2D ASTC_RGBA_6x6 492×180 stream | baca, repack, ganti tekstur ✅ |
| `atlas_test` | 2018.4 | Texture2D Crunch 1024×512 | baca, repack ✅ (pratinjau mentah) |
| Bundle sintetis | 2018.4 | TextAsset | baca, ganti teks, simpan, baca ulang ✅ |
| Bundle sintetis | 2018.4 | TextAsset | ganti nama + simpan + baca ulang ✅ |
| Bundle nyata | 2017.4–2018.4 | Texture2D | ganti nama + simpan + baca ulang ✅ |
| Dump data / pohon tipe | – | TextAsset, Texture2D | tampilan field & TypeTree ✅ |
| `.assets` mandiri | 2017.4 | SerializedFile tanpa UnityFS | baca, simpan, ganti tekstur ✅ |
| Blok DXT1/3/5, RGB24, RGBA32, RGB565, RGBA4444, BGRA32 | – | data sintetis | ✅ |

Uji repack memverifikasi: jumlah dan daftar aset identik, isi mentah setiap
objek identik, data node/`.resS` identik, serta hasil bisa dibuka ulang dan
diedit berulang kali.

**Belum teruji di perangkat:** jalur dekode GPU (EGL/GLES) dan pemutaran audio
tidak bisa dijalankan di sandbox tanpa GPU/emulator. Keduanya perlu dicoba
langsung di HP; bila GPU menolak suatu format, aplikasi otomatis mengekspor
data mentah dan menampilkan catatan.

Ikon launcher memakai logo UABEA dari proyek asli (lisensi MIT), dengan latar
gelap agar terbaca di launcher Android.
