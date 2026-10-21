# CNDN TV — Android TV starter project

Türkçe Android TV uygulaması için Kotlin/Media3 projesi. TV kumandası ile kanal zapping, numara girişi, EPG katmanı, son kanal hafızası, boşta katmanları gizleme ve oynatıcı hata durumunda yeniden deneme/atlama altyapısı içerir.

## Önemli yayın durumu

Bu ZIP **Android Studio proje kaynaklarını** içerir; derlenmiş APK içermez. Bu çalışma ortamında Android SDK ve Gradle kurulu olmadığından APK derlemesi çalıştırılamadı. `streamUrl`, `logoUrl` ve `xmltvId` alanları kanal kataloğunda bilerek `null` bırakılmıştır: yayıncıdan/ yetkili dağıtıcıdan kullanım izni ve doğrudan akış doğrulaması olmadan URL uydurulmamıştır. Bu nedenle ilk açılışta canlı yayın yerine bu durum açıkça gösterilir. Resmî web sayfaları doğrudan akış URL'si değildir.

## Gereksinimler

- Android Studio (Giraffe veya daha yeni)
- JDK 17
- Android SDK Platform 35
- İnternet bağlantısı (Gradle bağımlılıklarını indirmek için)

## APK oluşturma

1. ZIP dosyasını açın.
2. Android Studio'da `cndn_uydutv-main` klasörünü **Open** ile açın.
3. Gradle senkronizasyonunun tamamlanmasını bekleyin.
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)` seçin.
5. Debug APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.
6. TV'ye USB/ADB ile yüklemek için: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Komut satırından (Android SDK ve Gradle kuruluysa) proje kökünde `gradle assembleDebug` çalıştırın.

## Kontroller

- `CH+` veya `↓`: sonraki kanal; `CH-` veya `↑`: önceki kanal (kod ile uyumlu hale getirildi)
- `0–9`: kanal numarası, yaklaşık 1,2 saniye sonra geçiş
- `GUIDE` veya `G`: EPG katmanı
- `INFO` veya `I`: bilgi bandı
- `OK/ENTER`: 1 kez = kanal bilgisi; 2 kez (2 sn içinde) = solda kanal listesi, sağda seçili kanalın önizlemesi. Listede ◂/▸ ile kategori (Tüm Kanallar, Genel, Haber, Spor, Film, Çocuk, Müzik, Belgesel…) değiştirilir, ↑/↓ ile gezilir (kanal ~0,5 sn sonra sağda önizlenir), `OK` seçili kanalda tam ekrana geçer, `GERİ` önceki kanala döner
- `BACK`: açık katmanı kapatır; katman yoksa uygulamadan çıkar
- `PLAY/PAUSE/REWIND/FAST_FORWARD`: zaman kaydırma açılmaz; canlı konum korunur
- 5 saniye etkileşimsizlik: ekran katmanları kapanır

Bazı kumandalar `GUIDE`, `INFO` veya `CHANNEL_UP/DOWN` kodlarını göndermeyebilir; bu cihazlarda D-pad ve `G`/`I` alternatifleri kullanılabilir.

## Kanal / EPG kaynakları

Katalog: `app/src/main/java/com/uydutv/data/ChannelCatalog.kt`

- Resmî yayıncı sayfaları, referans sayfası olarak tutulur.
- XMLTV aday kaynağı: `https://iptv-epg.org/files/epg-tr.xml`
- EPG verisi yalnızca `xmltvId` ile birebir eşleşen kanallara atanır. Katalogdaki EPG ID'leri doğrulanana kadar boş olduğundan “Canlı” gösterilir.
- Akış adresi eklemek için yayıncı/yetkili dağıtıcı iznini alın, HLS/DASH adresini Android TV cihazında test edin ve `streamStatus = VERIFIED` olarak işaretleyin. `ChannelCatalog.playableStream()` yalnızca `VERIFIED` (siz doğruladıysanız) veya `USER_PROVIDED` (kendi seçtiğiniz oynatma listesinden gelen) akışları oynatır.

## Oynatma listesi (M3U)

Uygulama açılışta `ChannelCatalog.PLAYLIST_URL` adresindeki M3U listesini indirir, diske kaydeder ve kanalları `USER_PROVIDED` olarak işaretler. Bu listeler topluluk tarafından derlenir; yayıncı izni, erişilebilirlik veya coğrafi kısıt garantisi **yoktur**. Listeyi kullanma ve uygulamayı dağıtma sorumluluğu size aittir. Kendi/izinli bir liste kullanmak için `PLAYLIST_URL` değerini değiştirin.

## Kanal sırası

Kanal sırası ve numaraları `app/src/main/java/com/uydutv/data/ChannelOrder.kt` içindeki `slots` listesinden gelir (TV'deki numaralar boşluklarıyla korunur). Playlist kanalları adlarına göre eşleştirilir; Türkçe harf, boşluk ve HD eki farkı önemsenmez. Listede olmayan kanallar sona eklenir (`KEEP_UNMATCHED = false` ile gizlenir). Sıra değiştirmek için `slots` satırlarını düzenleyin.

## İmzalama (release)

Anahtar ve şifreler artık repoda tutulmaz. `app/build.gradle.kts` bunları ortam değişkenlerinden okur: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Tanımlı değilse release APK debug anahtarıyla imzalanır.

Kendi anahtarınızı üretin:

```
keytool -genkeypair -v -keystore release.jks -alias cndntv -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # çıktıyı GitHub secret olarak ekleyin
```

GitHub → Settings → Secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Daha önce paylaşılan eski `uydutv-release.jks` ve `uydutv123` şifresi ele geçmiş sayılmalı; kullanmayın.

## Dahil edilen işlevler ve bilinen sınırlar

- Media3 ExoPlayer + HLS + DASH modülleri
- TV Leanback launcher ve yatay tam ekran arayüz
- Son kanal kaydı
- XMLTV indirme/ayrıştırma ve güncel/sonraki program görünümü (kanal kimliği doğrulanmışsa)
- Oynatıcı hata durumunda 3 saniyede yeniden deneme
- Basit kısa statik/parazit hissi için kanal değişiminde geçiş
- Logo görselleri ve doğrudan yayın URL'leri henüz doğrulanmadı
- EPG yalnızca katalogdaki `xmltvId` ile eşleşen kanallar için belleğe alınır; diske kaydedilmez. İndirme başarısızsa programlar “Canlı” gösterilir ve sonraki liste yenilemesinde yeniden denenir
- Oynatma hatasında aynı kanal 3 kez denenir, olmazsa sonraki kanala geçilir (hepsi başarısızsa durur)
- Gradle wrapper dahil değil: Android Studio kullanın veya `gradle wrapper` ile üretin
- APK gerçek Android TV cihazında test edilmedi

## Resmî teknik referanslar

- Android TV uygulama başlangıcı: https://developer.android.com/training/tv/get-started/create
- Media3 HLS oynatma: https://developer.android.com/media/media3/exoplayer/hls
- Media3 sürümleri: https://developer.android.com/jetpack/androidx/releases/media3

## Açılış videosu

`app/src/main/res/raw/intro.mp4` dosyası varsa uygulama açılışında tam ekran oynatılır; kumandadan herhangi bir tuşla geçilir. Dosya yoksa logo animasyonu gösterilir. Dosya adı küçük harf olmalı (`intro.mp4`); kısa (5–10 sn), H.264/AAC ve 1080p ya da altında, mümkünse 10 MB altında olsun.

## Oynatma listeleri

Uygulama iki iptv-org listesini indirip birleştirir (aynı akış adresi tekrar edilmez): `countries/tr.m3u` (Türkiye) ve `languages/tur.m3u` (Türkçe yayın yapanlar). Kategori menüsü, listelerin `group-title` bilgisinden otomatik oluşur. Listeler `ChannelCatalog.PLAYLIST_URLS` içindedir.
