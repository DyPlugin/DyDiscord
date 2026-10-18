Current measured speed results: [1.0.2 delivery report](performance/2026-10-18/README.md). The feature research below is not performance evidence; no overall speed superiority was found.

# DyDiscord ve DiscordSRV karşılaştırması — 18 Ekim 2026

Kullanıcının sonraki açıklaması: istenen karşılaştırma özellik sayısı değil,
DyClaim/DyRTP grafiklerindeki gibi ölçülen hızdır. Aşağıdaki kaynak incelemesi
bu performans sorusunu yanıtlamaz. Henüz karşılaştırmalı süre ölçümü yoktur;
[ölçüm protokolü](performance-comparison-plan.md) ayrı hazırlanmıştır.

İncelenen sürümler: yerel DyDiscord 1.0.1 kaynakları ve DiscordSRV'nin son kararlı
[1.30.5 sürümü](https://github.com/DiscordSRV/DiscordSRV/releases/tag/v1.30.5).
DiscordSRV'nin çevrimiçi belgeleri zamanla değişebilir. Bu bir performans veya
güvenlik sertifikası değildir; iki eklenti aynı sunucuda karşılaştırmalı yük
testine sokulmadı. Yerel değişiklikler yayımlanan 1.0.1 JAR'ından farklıdır.

## Sonuç

DyDiscord'un DiscordSRV'den genel olarak daha iyi olduğu doğrulanmadı.
DiscordSRV daha geniş bir entegrasyon ve özellik kapsamı sunuyor. DyDiscord'un
kendi güçlü tarafları özel V2 hesap paneli, Türkçe/İngilizce komut akışı,
kişisel gelen sohbet tercihi ve mevcut kapsamındaki topluluk araçlarıdır.
Bu tercihler bütün sunucularda üstünlük veya daha az kaynak tüketimi anlamına gelmez.

| Alan | DyDiscord | DiscordSRV için doğrulanan bilgi |
| --- | --- | --- |
| Sohbet ve konsol | İki yönlü sohbet; konsol aktarımı ve komut yetkisi ayrı | Her iki özellik belgelenmiş; [genel belge](https://docs.discordsrv.com/) |
| Hesap eşleme | Özel modal, tek V2 panel veya slash komutları; süreli tek kullanımlık kod | Hesap eşleme komutları; ayrıca isteğe bağlı eşlemeden giriş engeli; [komutlar](https://docs.discordsrv.com/commands/), [giriş koşulu](https://docs.discordsrv.com/installation/link-to-join-setup/) |
| Kanal düzeni | Bir genel sohbet kanalı, ayrı panel ve konsol | Birden çok sohbet kanalı ve olay kanalı eşleştirmesi; [kanal ayarları](https://docs.discordsrv.com/config/#channels) |
| Sohbet eklentileri | Bilinmeyen sohbet dinleyicisinde çıkış durur; özel/genel kanal ayrımı için doğrulanmış adaptör gerekir | Herochat, LegendChat, LunaChat, TownyChat ve VentureChat için bağlantılar listelenmiş; [entegrasyonlar](https://docs.discordsrv.com/#plugins-we-hook-into) |
| Grup/rol eşitlemesi | LuckPerms; doğrudan/miras/geçici üyelik; yalnızca Minecraft → Discord | Yön ve yetkili kaynak ayarlanabiliyor; [eşitleme ayarları](https://docs.discordsrv.com/synchronization/) |
| Ek eşitlemeler | Nickname ve ban eşitlemesi kapsamda değil | Nickname ve ban eşitlemesi belgelenmiş; aynı eşitleme belgesi |
| Ses kanalları | Kapsamda değil | Yakınlığa bağlı Discord ses modülü; [ses belgesi](https://docs.discordsrv.com/voice/) |
| Ödüller ve çekiliş | Yapılandırılmış ödül komutları, kalıcı düğmeli çekiliş; ödülü sahibi teslim eder | Ödüllendirme genel özellik listesinde var. Çekilişin çekirdekte var/yok olduğu bu incelemede doğrulanmadı; eklentiler/addon'lar ayrı değerlendirilir |
| Dil | İngilizce ve Türkçe metinler, komut alias'ları ve ASCII karşılıkları | Birden fazla dil belgelenmiş. Güncel Türkçe kapsamı ayrıca doğrulanmadığı için yok denmiyor |
| Dağıtım | Küçük paket kütüphaneleri sunucu çözücüsüyle indirir; standalone içerir | Küçük yükleme dosyası toplam disk/RAM/CPU kullanımını göstermez; paket boyutlarından performans sonucu çıkarılmadı |

İki üründe ortak olan bir özellik tek başına üstünlük olarak işaretlenmedi.
DiscordSRV kaynak kodu kopyalanmadı. Ses, ban/nickname eşitlemesi, çoklu kanal,
zorunlu eşleme veya yeni moderasyon özellikleri bu turda eklenmedi.

## Bu incelemeden çıkan düzeltmeler

1. Minecraft'a gelen ad/rol/yanıt etiketindeki `_` ve `|` gibi karakterler topluca
   siliniyordu. `the_claww` farklı bir ad gibi görünüyordu. Kimlik metinleri
   korunuyor; mesaj gövdesindeki gerçek Markdown biçimleri mevcut JDA
   MarkdownSanitizer ile düzleştiriliyor. Renk/kontrol temizliği ve kalın yazı
   engeli sürüyor.
2. Bilinmeyen sohbet eklentisi aktarımı durdururken durum çıktısı yalnızca
   yapılandırılmış açık değerini gösteriyordu. Durum/kontrol artık gerçek
   duraklamayı ve en fazla sekiz, tekilleştirilmiş güvenli eklenti adını gösterir.
   Yetkili yöneticilere İngilizce/Türkçe sınırlı uyarı iletilir. Dinleyici
   kaldırılınca tanı yeniden güncel durumu gösterir. Kalıcı Paper API hatası
   ayrıca tanınır; tek bir geçici olay hatası tüm adaptörü kalıcı kapatmaz.

Gizlilik nedeniyle durdurma kuralı kaldırılmadı. Tanı, üçüncü taraf eklentiyi
devre dışı bırakmayı veya kanal değiştirerek korumayı aşmayı önermiyor; uyumlu
genel sohbet entegrasyonu gerektiğini açıklıyor.

## Doğrulama

İlk regresyon çalıştırması: 34 test, beklenen 4 başarısızlık. Adların silinmesi ve
yöneticilere uyumsuzluk uyarısı ulaşmaması mevcut kodda yeniden üretildi.
İlk ek tanı testinde mesaj dosyası yanlış ayar API'siyle düzenlenmeye çalışıldı;
test gerçek dosya düzenleme + reload yolunu kullanacak şekilde düzeltildi.

Son incelemede yanıt yazarının üretim akışında yeniden temizlenmesi ve geçici
Paper hedef hatasının InvocationTargetException içinde kalıcı hata sayılması
ayrıca üretildi: 33 testten 2 beklenen başarısızlık. Bu iki regresyon düzeltildi;
eksik Paper API için kalıcı durdurma testi de korundu. Bağımsız salt okunur son
incelemede ek somut regresyon bulunmadı.

- Son hedefli test komutu başarılı: ChatBridgeTest, ChatTextIdentityTest,
  ChatCompatibilityTest ve AdminWarningsTest.
- `gradlew.bat clean test build --console=plain`: **672 test**, 0 başarısız,
  0 hata, 0 atlanan; BUILD SUCCESSFUL. Bu turda 13 yeni test eklendi.
- Artifact testleri izole JDA/SQLite yüklemesini, 20 SQLite native kaynağını ve
  JDBC servis kaydını doğruladı. Ek paket incelemesinde her iki JAR'ın sürümü
  1.0.1, eklenti sınıflarının Java bytecode major değeri 61 (Java 17), lisans
  kaynağı sayısı 14 ve runtime/veritabanı/log/yedek girdi sayısı 0 bulundu.
- Küçük paket bağımlılıkları sunucu üzerinden çözer; standalone bunları içerir.
  Java hedefi, bağımlılık kilitleri ve veritabanı şeması değiştirilmedi.

| Yerel aday | Bayt | SHA-256 |
| --- | ---: | --- |
| `DyDiscord-1.0.1.jar` | 2179611 | `5e0b46897f144630cd597e646720f6dc54d3bd740264dc7eddde1355ffcc7b6b` |
| `DyDiscord-1.0.1-standalone.jar` | 24098439 | `c4f61e28cb445ada952c913003e487aac52d5975f2d551d4a02f5d514d7c3b10` |

Kaynak değişiklikleri: chat/ChatText.java, chat/ChatBridge.java,
chat/ChatEvents.java, discord/Bot.java, language/Messages.java,
AdminWarnings.java ve messages_en/tr.yml. Testler: ChatTextIdentityTest,
ChatCompatibilityTest ve ChatBridgeTest. Belgeler: bu rapor, chat-bridge.md,
CHANGELOG.md ve AGENTS.md. Yeni mesaj anahtarlarının altında kısa İngilizce
amaç açıklamaları bulunur; özel çevirileri koruma testi vardır.

Kanıt dosyaları `.local/discordsrv-review-20261018/` altında: red.log,
review-red.log, review-green.log, full-final.log, full-test-results/,
test-totals.json, package-check.json ve reproducible.log. Önceki kaynak
kopyaları before/ içindedir; Git deposu oluşturulmadı.

Gerçek Discord/Paper/Essentials/VentureChat ve karşılaştırmalı yük testi bu turda
yapılmadı. Yeni sonuçlar eski canlı kanıtlara mal edilmez. İstemci görünümü,
gerçek sohbet eklentileri ve ağ arızası için test sunucusunda ayrı kabul gerekir.
Yayımlanmış aynı sürüm JAR'ı değiştirilmedi; yerel adayı kurmadan önce sunucuyu
durdurup veri klasörünü yedekleyin. Platform yayını veya üretime kurulum yapılmadı.

`gradlew.bat clean build -x test --console=plain` ile ikinci temiz derleme başarılı.
Her iki paket de yukarıdaki SHA-256 değerlerini aynen üretti. Test XML raporları
clean öncesinde full-test-results/ klasörüne korundu. Yerel adayların ayrı kopyası
`.local/discordsrv-review-20261018/artifacts/` altındadır; build/libs/ aynı paketleri
içerir. Bunlar yayımlanmış 1.0.1 dosyasının yerine otomatik geçirilmedi.
