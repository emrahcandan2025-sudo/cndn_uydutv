# UyduTV — Android TV starter project

Türkçe Android TV uygulaması için Kotlin/Media3 projesi. TV kumandası ile kanal zapping, numara girişi, EPG katmanı, son kanal hafızası, boşta katmanları gizleme ve oynatıcı hata durumunda yeniden deneme altyapısı içerir.

## Önemli yayın durumu

Bu ZIP **Android Studio proje kaynaklarını** içerir; derlenmiş APK içermez. Bu çalışma ortamında Android SDK ve Gradle kurulu olmadığından APK derlemesi çalıştırılamadı. `streamUrl`, `logoUrl` ve `xmltvId` alanları kanal kataloğunda bilerek `null` bırakılmıştır: yayıncıdan/ yetkili dağıtıcıdan kullanım izni ve doğrudan akış doğrulaması olmadan URL uydurulmamıştır. Bu nedenle ilk açılışta canlı yayın yerine bu durum açıkça gösterilir. Resmî web sayfaları doğrudan akış URL'si değildir.

## Gereksinimler

- Android Studio (Giraffe veya daha yeni)
- JDK 17
- Android SDK Platform 35
- İnternet bağlantısı (Gradle bağımlılıklarını indirmek için)

## APK oluşturma

1. ZIP dosyasını açın.
2. Android Studio'da `UyduTV` klasörünü **Open** ile açın.
3. Gradle senkronizasyonunun tamamlanmasını bekleyin.
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)` seçin.
5. Debug APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.
6. TV'ye USB/ADB ile yüklemek için: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Komut satırından (Android SDK ve Gradle kuruluysa) proje kökünde `gradle assembleDebug` çalıştırın.

## Kontroller

- `CH+` veya `↓`: sonraki kanal; `CH-` veya `↑`: önceki kanal
- `0–9`: kanal numarası, yaklaşık 1,2 saniye sonra geçiş
- `GUIDE` veya `G`: EPG katmanı
- `INFO` veya `I`: bilgi bandı
- `OK/ENTER`: bilgi bandı kapalıyken açar, açıkken rehberi açar
- `BACK`: katmanı kapatır
- `PLAY/PAUSE/REWIND/FAST_FORWARD`: zaman kaydırma açılmaz; canlı konum korunur
- 5 saniye etkileşimsizlik: ekran katmanları kapanır

Bazı kumandalar `GUIDE`, `INFO` veya `CHANNEL_UP/DOWN` kodlarını göndermeyebilir; bu cihazlarda D-pad ve `G`/`I` alternatifleri kullanılabilir.

## Kanal / EPG kaynakları

Katalog: `app/src/main/java/com/uydutv/data/ChannelCatalog.kt`

- Resmî yayıncı sayfaları, referans sayfası olarak tutulur.
- XMLTV aday kaynağı: `https://iptv-epg.org/files/epg-tr.xml`
- EPG verisi yalnızca `xmltvId` ile birebir eşleşen kanallara atanır. Katalogdaki EPG ID'leri doğrulanana kadar boş olduğundan “Canlı” gösterilir.
- Akış adresi eklemek için yayıncı/yetkili dağıtıcı iznini alın, HLS/DASH adresini Android TV cihazında test edin ve `streamStatus = VERIFIED` olarak işaretleyin. Yalnızca `ChannelCatalog.playableStream()` tarafından doğrulanmış akışlar oynatılır.

## Dahil edilen işlevler ve bilinen sınırlar

- Media3 ExoPlayer + HLS + DASH modülleri
- TV Leanback launcher ve yatay tam ekran arayüz
- Son kanal kaydı
- XMLTV indirme/ayrıştırma ve güncel/sonraki program görünümü (kanal kimliği doğrulanmışsa)
- Oynatıcı hata durumunda 3 saniyede yeniden deneme
- Basit kısa statik/parazit hissi için kanal değişiminde geçiş
- Logo görselleri ve doğrudan yayın URL'leri henüz doğrulanmadı
- EPG önbelleği diske kaydedilmiyor; indirme başarısızsa programlar “Canlı” olarak gösterilir
- APK gerçek Android TV cihazında test edilmedi

## Resmî teknik referanslar

- Android TV uygulama başlangıcı: https://developer.android.com/training/tv/get-started/create
- Media3 HLS oynatma: https://developer.android.com/media/media3/exoplayer/hls
- Media3 sürümleri: https://developer.android.com/jetpack/androidx/releases/media3
