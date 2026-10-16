# DyDiscord 1.0.0

Küçük `DyDiscord-1.0.0.jar`, kütüphaneleri ilk kurulumda Spigot/Paper'ın
yerleşik yükleyicisiyle indirir; sunucunun kütüphane önbelleğinde saklanırlar.
İlk kurulum internet gerektirir ve daha uzun sürebilir. Bağımlılıklar alınamazsa
eklenti yüklenemez. Çevrimdışı kurulum için `-standalone.jar` tam paketini kullanın.
İki paketi aynı anda `plugins` klasörüne koymayın. Sunucunun sürüm seçimi veya
geçişli bağımlılıkları yapılandırılan Maven kütüphanelerinden farklılaşabilir.

1.0.0 topluluk araçlarını ekler; mevcut özellikler, ayarlar ve hesap verileri
korunur. Yalnızca kullanılmayan koleksiyon sınıfları çıkarılır. SQLite'ın bütün
platform dosyaları, servis kayıtları, lisanslar ve Trove kaynak arşivi korunur.

Panel yerine komut kullanmak için `/dydiscord admin panel kapat` yazın.
`/dydiscord admin panel aç` aynı panel mesajını yeniden etkinleştirir.
Dosyadan ayarlamak için `bot.yml` içinde `panel.enabled: false` yapıp
`/dydiscord admin reload` kullanın. Discord `/eşle`, `/hesap`, `/kaldır`
çalışmaya devam eder; dy! komutları panel bağlantısı yerine ilgili / komutunu
gösterir. [Panel/komut modu rehberi](panel-mode.md).

**Claww · DyPlugin** tarafından geliştirilen DyDiscord, Minecraft sunucusu içinde
Discord botu çalıştırır. Özel hesap eşleme ve isteğe bağlı özel konsol kanalı sunar.
Ayrı Node.js botu veya harici veritabanı gerekmez. İngilizce ve Türkçe dahildir.

Bu sürüm 1.0.0 adını kullanır; otomatik kontroller uygulanır. Gerçek Minecraft/Discord,
istemci ve mobil testleri tamamlanmadığı için yayına hazır kabul edilmez.
Uyumluluk adayları ve test sonuçları [test belgesindedir](compatibility-test.md).
Proje, DyClaim'e benzer GPL v3 ve ek koşullar içeren lisans metnini kullanır.
DyDiscord adı ve Claww / DyPlugin telif bilgisi korunur; ücretli dağıtım yasaktır.
Dağıtılan değişikliklerin kaynakları aynı koşullarla paylaşılmalıdır.
DyClaim bağımlılığı yoktur. [Lisans metni](../LICENSE).

## Derleme ve kurulum

v1.0.1-BETA, isteğe bağlı Minecraft → Discord rol eşitlemesini ekler. Eşlenen
hesaba bağımsız bir rol verilebilir; gerçek LuckPerms grup üyelikleri, sabit
bağlamlarla sorgulanıp Discord rollerine dönüştürülebilir. Rol eşitlemesi ilk
kurulumda kapalıdır. Discord rolleri Minecraft grubu, izin veya OP vermez.
`all` eşleşen kuralları birleştirir; `highest` en yüksek önceliği seçer ve eşit
öncelikleri birleştirir. LuckPerms bilgisi alınamazsa grup üyeliği bilinmiyor
sayılır; mevcut grup rolleri korunur, bağımsız eşlenen hesap rolü çalışabilir.

`/dydiscord admin roller durum` durumu, `/dydiscord admin roller önizle <uuid>`
değişiklikleri gösterir. `eşitle <uuid>` veya `eşitle tümü` işi sıraya alır.
`eşlenen liste/ekle/çıkar` ve `gruplar liste/ekle/çıkar` ayrı yaprak izinlere
sahiptir. Her yaprak `dydiscord.admin.roles.*` altındadır; oyunculara varsayılan
rol yönetimi verilmez. ASCII karşılıklar da kabul edilir.

Rol vermeden önce Manage Roles iznini ve botun rol sırasını doğrulayın. Yönetilen,
ayrıcalıklı ve konsol erişim rolleri otomatik ekleme/kaldırmada engellenir.
Üye intent'i alınamazsa dönemsel REST eşitlemesi devam eder. Rol eşitlemesini
kapatmak verilen rolleri kendiliğinden silmez. `temizle önizle`, ardından 30 saniye
içinde `temizle onayla <token>` ile yalnızca yönetilen roller için temizleme
başlatılır; onay sırasında özellik kapalı olmalıdır. Yeniden açma temizlemeyi
iptal eder veya normal eşitlemeyle değiştirir. [Ayrıntılı rol rehberi](role-sync.md).

İsteğe bağlı boost ödülleri, eşlenen ve Discord sunucunuzu boostlayan hesap için
kendi ekonomi, site kredisi veya rütbe eklentinizin konsol komutlarını çalıştırır.
`bot.yml` içinde `boost-rewards.enabled: false` ve komut listeleri boş başlar.
Yalnızca `{player}` ve `{uuid}` desteklenir; para veya site sağlayıcısı dahil değildir.
Mevcut bir LuckPerms grubu için `lp user {uuid} parent addtemp booster 30d`
30 günlük rütbe verir. Süreyi LuckPerms yönetir; oyuncu çevrimdışıyken de üyelik
süresi biter. Bu grubu ayrı `roles.groups` kuralıyla Discord rolüne bağlayabilirsiniz.

Ödül, kişinin kesintisiz boost dönemi için bir kez kaydedilir; satın aldığı her
boost başına veya her hafta verilmez. Yeniden başlayan dönemde de en az 168 saat
bekleme vardır. Boost bitince veya hesap eşlemesi kaldırılınca isteğe bağlı
`remove-commands` çalışabilir. Belirsiz komutlar kendiliğinden tekrar edilmez.
[Ödül ayarları, örnekler ve kayıtlar](boost-rewards.md).

SQLite şeması 3'tür. Eski şema 1/2, WAL verisini içeren tutarlı `VACUUM INTO`
yedeği alındıktan sonra mevcut eşlemeleri, adları ve kayıt sürümlerini koruyarak
taşınır. v1.0.0-BETA yeni şemayı açmaz; geri dönüş için eski JAR ve ona uygun
eski yedek birlikte gerekir. Yerel yedek Discord'daki rol değişikliklerini geri
almaz. [Yedek ve geri dönüş](upgrade-and-rollback.md).

Windows'ta `gradlew.bat clean build`, diğer sistemlerde `./gradlew clean build`
çalıştırın. Gradle Wrapper 9.1.0, bağımlılık kilidi ve SHA-256 doğrulaması dahildir.
JDK 25.0.4.1 ile Java 17 bytecode ve Spigot API 1.20.4 tabanı derlendi.
Bu bilgi gerçek sunucu uyumluluğu kanıtı değildir. Folia, proxy,
Floodgate/Bedrock ve sunucu açıkken JAR değiştirme desteği ilan edilmez.

1. `build/libs/DyDiscord-1.0.0.jar` dosyasını `plugins` dizinine koyup
   sunucuyu başlatın. `plain` JAR kurulmaz. Ayarlar, mesajlar ve SQLite oluşturulur.
   Bot, log aktarımı ve uzaktan komutlar varsayılan kapalıdır. Tokensiz kurulum
   eklentiyi çökertmez.
2. [Discord Developer Portal](https://discord.com/developers/applications)
   üzerinden kendi botunuzu oluşturun. `bot` ve `applications.commands` kapsamları
   ile davet edin. Bot hesabına Administrator vermeyin.
3. Yerel `plugins/DyDiscord/bot.yml` içinde token ve tırnaklı `guild-id` yazın.
   `DYDISCORD_BOT_TOKEN` ortam değişkeni dosyanın önüne geçer. Tokeni komutlara,
   mesajlara veya destek raporlarına koymayın. Aynı token başka süreçte kullanılmaz.
4. `/dydiscord admin davet https://discord.gg/Kodunuz` ile daveti ayarlayın.
   `config.yml` içinde `server-name` değerini düzenleyin. Dosya değişikliklerinden
   sonra `/dydiscord admin yenile`, ardından `/dydiscord admin bot aç` kullanın.
5. `/dydiscord admin panel kanal <id>` ve `/dydiscord admin panel gönder` çalıştırın.
   Tek Components V2 Container; avatarı ve her hesap işleminin kısa açıklamasını
   aynı kartta toplar. Metin ile üç hesap butonu arasında küçük ayraç bulunur;
   kartta marka etiketi veya alt yazı yoktur. Sonraki gönderme/yenileme işlemleri kayıtlı tek mesajı
   günceller; eski klasik panel de aynı mesajda dönüştürülür.
   `/dydiscord admin kontrol` kurulumu kontrol eder.

İlk `bot.yml` düzenlemesini bot kapalıyken `/dydiscord admin yenile` ile yükleyin.
Yerel ayarlar kaydedilir; Discord kanal/rol erişimi bağlantıdan sonra kullanım
öncesinde doğrulanır. Ardından `/dydiscord admin bot aç` kullanılabilir. Eksik token
veya guild için kurulum mesajı gösterilir. Bağlı botun etkin kanal politikası
değişiklikleri canlı doğrulama gerektirmeye devam eder.

Varsayılan Minecraft öneki mavi: `&7[&9DyDiscord&7]`. Eski tam varsayılan
`&7[&dDyDiscord&7]` başarılı açılış/reload'da güncellenir; özel önek korunur.

Botun kanallarda View Channel, Send Messages, Embed Links ve Read Message History
izinleri gerekir. Mesaj silme/yönetme, pinleme ve çevrimiçi durum izinleri gerekmez.
İsteğe bağlı rol eşitlemesi için Manage Roles izni ve uygun rol sırası gerekir.
Üye olayları açıldığında Server Members intent kullanılır; bu intent alınamazsa
dönemsel REST eşitlemesi devam eder. Boost ödülleri de bu intent'i ister; intent
yoksa güncel üye bilgisi REST ile aralıklı kontrol edilir. İstenen intent
değiştiğinde bot yeniden başlatılmalıdır.
`dy!` komutları ve düz konsol girişi için Message Content intent açılmalıdır.
Intent reddedilirse slash/panel için yeniden bağlanılır; prefix ve konsol girişi
kapalı, bot durumu DEGRADED olur. Gerçek bot adı, avatarı ve uygulama açıklaması
Portal'dan düzenlenir; panel başlığı bot profilini değiştirmez. DyDiscord bot
profilinde etkinlik/durum yazısı ayarlamaz. Eski `bot.activity-text` değeri
etkisizdir; bağlantı tanısı için durum komutları kullanılabilir.

Panel tek Discord kartıdır: Hesap Eşle özel kod formunu açar, Hesabım kendi
eşlemenizi gösterir, Eşlemeyi Kaldır özel onay başlatır. Başlık, açıklama ve
gizlilik notu tek metin bloğundadır; işlev açıklamalarının arasında birer boş satır
bulunur. Küçük ayraç metni alttaki üç hesap butonundan ayırır. Buton sırası
Hesap Eşle → Eşlemeyi Kaldır → Hesabım şeklindedir. Panelde marka etiketi
veya alt yazı bulunmaz. Komut ve hesap yanıtlarında
tekrar eden marka alt yazısı bulunmaz. Panel mesajında klasik embed yerine Container
kullanılır; kurtarma işareti kullanıcıya gösterilmez.

## Oyuncu komutları

`/discord help` ve `/dydiscord help` aynı yardım menüsünü açar. Türkçe `yardım`
ve `yardim` da kullanılabilir. Menüdeki bütün komutlar `/dydiscord` ile gösterilir.
Türkçe eşleştirme kaldırma komutu `/dydiscord kaldır`; eski `ayır/ayir` de çalışır.
Yönetim için `/dydiscord admin yardım` kullanın; eski `yönetim/yonetim` girişleri de kabul edilir.

| Yol | İngilizce / ASCII | Yaprak izin |
| --- | --- | --- |
| `/discord` | aynı | `dydiscord.invite` |
| `/discord yardım` | `help`, `yardim` | `dydiscord.help` |
| `/discord eşle` | `link`, `esle` | `dydiscord.link` |
| `/discord durum` | `status` | `dydiscord.status` |
| `/discord kaldır` | `unlink`, `kaldir`; eski `ayır/ayir` de çalışır | `dydiscord.unlink` |
| `/discord onayla <kod>` | `confirm` | bekleyen işlemin izni |
| `/discord iptal` | `cancel` | bekleyen işlemin sahibi |
| `/discord dil <en\|tr\|auto>` | `language`, `lang` | `dydiscord.language` |

`/dydiscord` aynı yönlendiricidir. `/link`, `/esle`, `/eşle`, `/hesap-esle`,
`/hesap-eşle` kısa yolları kaydedilir; Unicode kökler canlı sunucuda doğrulanmadı.
Komut çakışmasında `/dydiscord eşle` veya `/dydiscord:dydiscord eşle` kullanın.
Başka eklentinin komutu zorla ele geçirilmez. Davet bot kapalıyken de sadece
komutu yazana gönderilir.

Yardım bütün izinli oyuncu komutlarını, davet ve onay/iptal dahil gösterir. Konsolda
oyuncuya özel satırlar gizlenmez; “sadece oyuncular” notu taşır. Bu komutların
çalıştırılması yine oyuncu gerektirir. Oyunda `/discord dil tr` kişisel tercihi,
`/dydiscord admin dil tr` sunucunun genel dilini değiştirir; genel komut konsolda da
çalışır. Genel `en/tr` zorlamasında kişisel tercih saklanır ve zorlanan dil bildirilir.

Oyunda kod üretin; yanıt yalnızca kodu içerir. Davet gerekirse ayrıca `/discord`
kullanın. Discord panelinin Hesap Eşle butonuna basıp açılan forma kodunuzu girin.
Yeni kurulumlarda varsayılan kod 8 rastgele, karışmayan karakterdir; ayarlanmış
özel uzunluklar korunur. Kod 5 dakika geçerlidir. Yeni kod
için 30 saniye beklenir. Oyundan çıkmak kodu iptal eder. Kodlar sunucu yeniden başladığında geçersiz
olur. Kullanım anında sahibin mevcut çevrimiçi oturumu ve yaprak izni yeniden
kontrol edilir. Eşleme izin veya OP vermez. Bir UUID ve bir Discord hesabı yalnızca
birbirine eşlenebilir. İki taraftaki başarı metni Minecraft adı ve Discord kullanıcı
adını gösterir. Özel durum yanıtı bu adları içerir; ham hesap ID'si veya tarih
göstermez. Discord adı bulunmayan eski kayıtlarda yerelleştirilmiş hesap açıklaması
kullanılır. Eşleme kaldırma özeldir. Kaldırma, varsayılan
30 saniyelik tek kullanımlık aktör/kayıt sürümü onayı ister. Bot kapalıyken yerel
durum ve kaldırma çalışabilir. Yeni kod için botun kullanılabilir olması gerekir.

`online-mode=false` sunucularda da kod üretimi ve eşleme çalışır. Offline sunucuda
hesap sahipliğini login sistemi doğrulamalı ve giriş tamamlanmadan bütün DyDiscord
komut köklerini/alias'larını engellemelidir. DyDiscord login eklentisinin API'sinden
giriş durumu sorgulamaz; çevrimiçi oturum ve yaprak iznini kontrol eder. Kimlik
modunu değiştirmek UUID'leri değiştirebilir; eski eşlemeler otomatik taşınmaz.

## Yönetim ve izinler

Aşağıdaki yollar `/dydiscord admin` altındadır. İngilizce karşılıklar da kabul edilir.
Yerel konsol ve RCON yönetebilir; oyuncunun kendi hesabı oyuncu gerektirir.

| Yol | `dydiscord.admin.` sonrası izin |
| --- | --- |
| `help` / `yardım` | erişilebilir en az bir yönetici yaprağı |
| `davet <url>` | `invite` |
| `dil <en\|tr\|auto>` | `language` |
| `yenile` | `reload` |
| `durum` | `status` |
| `kontrol` | `doctor` |
| `bot aç\|kapat\|yenidenbaşlat` | `bot` |
| `panel kanal <id>` | `panel.channel` |
| `panel gönder` / `panel yenile` | `panel.send` / `panel.refresh` |
| `konsol kanal <id>` | `console.channel` |
| `konsol aç\|kapat` | `console.toggle` |
| `konsol komutlar aç\|kapat` | `console.commands` |
| `konsol rol ekle\|çıkar <id>` / `konsol rol liste` | `console.roles` |
| `konsol yönetici on\|off` | `console.administrator` |
| `kaldır <uuid> <gerekçe>` | `unlink` |

`/discord ayarla <url>` ve `/discord setinvite <url>` doğrudan yönetici davet
yoludur. Türkçe ASCII karşılıkları `yardim/esle/ayir/gonder/cikar/ac/yonetici/
yenidenbaslat` kabul edilir. URL, ID, UUID, ad ve gerekçe dönüştürülmez.
Argüman sayısı doğrulanır.

`dydiscord.player` varsayılan true ile oyuncu yapraklarını; `dydiscord.admin`
varsayılan op ile bütün yaprakları içerir. Yaprakların kendi varsayılanı false'dur.
Açık false yaprak OP için de reddedilir; tek yönetici yaprağı üst grup gerektirmez.
Yardım ve TAB etkin dilde, izin verilen komutları gösterir. Discord Administrator
ve hesap eşleme Minecraft yetkisi vermez.

## Dil ve ayarlar

`lang: en` veya `lang: tr`, sunucudaki herkesin dilini belirler. `lang: auto`
seçildiğinde kişisel tercihiniz kullanılır. Kişisel tercihiniz de `auto` ise
Minecraft'ın diline bakılır: Türkçeyse Türkçe, diğer dillerde İngilizce kullanılır.
Konsolda `auto`, İngilizce anlamına gelir. Sunucu dili zorunlu olsa da kişisel
tercihiniz saklanır. `/dydiscord admin dil tr` sunucunun genel dilini değiştirir.

Discord botunun dili `bot.language: en/tr` ile ayrıca seçilir. Bot anahtarı,
Discord sunucusu, bot dili veya gerekli izinler değiştiğinde ayarları yenileyip
botu yeniden başlatın. Durum ekranı seçilen dili ve gerçekten kullanılan dili
ayrı gösterir. Yalnızca DyDiscord'un kaydettiği Discord komutları güncellenir;
başka bir komutla ad çakışırsa işlem durur.
Slash yolları `/yardım`, `/eşle`, `/hesap`, `/kaldır`, `/durum`, `/sunucu`; İngilizcede
`/help`, `/link`, `/account`, `/unlink`, `/status`, `/server` olur. Prefix iki dil ve ASCII
alabilir. `dy!eşle/hesap/kaldır` paneli gösterir. Bu yanıt herkese görünür ama
hesap bilgilerinizi veya kodunuzu içermez. `/eşle` doğrudan kod girme ekranını
açar. Hesap bilgileri ve kaldırma onayı yalnızca size gösterilir.
`/sunucu`, `dy!sunucu` veya `dy!server`; sunucu adını, görünür çevrimiçi oyuncu
sayısını/kapasiteyi ve Minecraft sürümünü gösterir. Oyuncu isimleri listelenmez.

Bir ayar veya mesaj dosyası bozuksa önceki çalışan ayarlar korunur. Eksik bir
çeviri için İngilizce metin kullanılır ve sunucu kaydına uyarı yazılır. Eski
varsayılan Türkçe metinler yeni çeviriye geçirilir; kendi yazdığınız metinler ve
dosya yorumları korunur. Bot kapalıyken de `/dydiscord admin yenile` ile ilk
ayarlar yüklenebilir. Panel, kayıt aktarımı ve konsol komutlarında erişim izinleri
kullanım anında tekrar kontrol edilir. Discord ID'lerini tırnak içinde yazın.

## Özel konsol

Panelden farklı özel metin kanalı seçin. `@everyone` View Channel kapalı olmalı;
yalnızca güvenilen yönetici rolleri ve bot erişebilmelidir. Bot dışındaki kişisel
üye allow override'ları ihtiyatlı biçimde reddedilir. Administrator kanal retlerini
aşabilir. Önce izinli rolleri belirleyin, kanalı seçin, log aktarımını ve uzaktan
komutları ayrı açın. Rolün kaldırılması kanalı eski role açık bırakacaksa politika
değişimi reddedilir; Discord kanal erişimini de düzeltin.

İzinli kişinin bu kanala yazdığı yeni düz mesaj gerçek Minecraft **konsol** komutudur.
`op`, izin yönetimi ve bütün eklenti komutları dahil tam güç verir. Kısıtlı moderatör
alanı değildir. Bot/webhook, DM/thread, düzenlenmiş/sistem mesajı, ek dosya, çok satır,
aynı mesaj ID'si ve `dy!` komutları çalıştırılmaz. Çalıştırmadan önce üyelik/politika
yeniden kontrol edilir; Bukkit dispatch ana thread'dedir.
Sınırlar: kullanıcı başına 2 saniyede 1; kanalda dakikada 20; 50 bekleyen komut;
log kuyruğunda 2.000 satır/1 MiB ve 2 saniye flush. Taşmada eski satırlar düşer,
özet gösterilir. Audit/mesaj-ID kaydı dispatch öncesi diske yazılır. Çökme sonrası
belirsiz komutlar tekrar çalıştırılmaz. Kanalda yalnızca sunucunun gerçek konsol
çıktısı görünür. Kabul, ret, kuyruk veya çalıştırma hatası ek bot cevabı üretmez;
sonuç yerel audit/hata kaydında tutulur. Çıktı üretmeyen komut sessiz kalır. Yerel
`dispatched` kaydı komutun iş sonucunun başarılı olduğunu kanıtlamaz; `not-found`
bilinmeyen komutu politika reddinden ayırır. Çevrimiçi oyuncuları görmek için
`list` yazın; `online` için o komutu sağlayan ayrı bir eklenti gerekir.

## Veri ve sınırlar

SQLite UUID, Discord ID, son Minecraft adı ve Discord kullanıcı adı, UTC eşleme zamanı, kayıt sürümü,
dil tercihi, tek panel, yönetilen slash ID'leri ve sınırlı konsol kabul kayıtlarını
tutar. Kod/onaylar bellektedir; kod yalnızca tuzlu digest ile doğrulanır. Unlink
aktif kaydı siler; audit ve yedekler eski kimlik bilgilerini içerebilir. Audit
30 gün/20 MiB sınırına tabidir. Discord kanal geçmişini eklenti yönetmez.
Discord kullanıcı adı görüntüleme verisidir; kimlik ve tekillik ID ile korunur.
Veritabanı geçişi mevcut hesap kayıtlarını korur. Boost ödüllerinin kalıcı dönem
ve kaldırma kayıtları ayrı `data/boosts.db` dosyasındadır; şeması 1'dir. Eski ödül
yedeğine dönmek daha önce verilmiş dış kredinin tekrar verilmesine yol açabilir.
Ödül kayıtları, tekrar ödemeyi engellemek için eski UUID/Discord ID ve adı hesap
eşlemesi kaldırıldıktan sonra da saklar.
İki yönlü sohbet köprüsü, kapanış bildirimi, isteğe bağlı rol eşitlemesi ve boost
komutları kapsamdadır. Müzik, web paneli, telemetri ve otomatik JAR yüklemesi yoktur.
Bilinen sırlar maskelenir; üçüncü tarafların her sırrını bulma garantisi verilmez.
Açılış öncesi/kapanış sonrası loglar, çökme ve ağda kaybolan çıktılar kapsam dışıdır.

Hata durumunda mevcut veriyi koruyun. Belirsiz panel gönderimini kör tekrar etmeyin
ve iç durum kayıtlarını elle düzenlemeyin. [Güvenlik](security.md),
[yedek/geri dönüş](upgrade-and-rollback.md) ve [test kapsamı](compatibility-test.md) belgelerine bakın.

## Panel resmi ve güncelleme bildirimi

Panelde varsayılan olarak Discord sunucunuzun resmi görünür. `bot.yml` içindeki
`panel.image-url` alanına HTTPS resim bağlantısı yazarak değiştirebilirsiniz.
Boş bırakırsanız sunucunun resmi kullanılır; sunucu resmi yoksa resim alanı gizlenir.
Dosyayı kaydettikten sonra `/dydiscord admin yenile` ve
`/dydiscord admin panel yenile` çalıştırın.

Eski ayarlar özel değerler ve yorumlar korunarak tanınır. Yeni seçenekler ve
kısa İngilizce açıklamalar eklenir; değişen dosyaların önce yedeği alınır.
Değişmeyen dosya yeniden yazılmaz. Aynı özgün baytlar için SHA-256 adlı yedek
tekrar kullanılır; eski JAR'ların kendi davranışı bu güncellemeyle değişmez.
`config-version` 1’den 2’ye geçirilir; desteklenmeyen yeni şema reddedilir.

`config.yml` içindeki `updates.enabled`, Modrinth’teki `dydiscord` projesini
altı saatte bir kontrol eder. Sunucunuza uygun yeni sürüm çıktığında konsola
ve `dydiscord.admin.update` izni olan oyunculara haber verir. Beta’dan kararlı
sürüme geçiş tanınır. JAR indirilmez veya kendiliğinden yüklenmez. Proje
şu anda yayınlanmadığı için API 404 döndürüyor; yayınlandığında kontrol çalışır.

Sohbet köprüsü, ayarlar ve sınırlar: [rehber](chat-bridge.md).
İzole gerçek LuckPerms/JDA rol kontrolleri ve yeni JAR'ın doğrulama sınırları:
[devam doğrulaması](continuation-verification-20261012.md). Gerçek ücretli boost,
para/site kredisi, insan eşleme formu ve Java 17 çalışma ortamı ayrı canlı kanıt
gerektirir; Java 17 bytecode hedefi bunları doğrulamaz.

## 1.0.0 topluluk araçları

Discord tarafında `/oyuncular`, `/ip`, `/bağlantılar` ve genişletilmiş `/sunucu`
komutları bulunur. Adresleri siz ayarlarsınız; gerçek makine IP’si paylaşılmaz.
Oyuncular `/dydiscord sohbet gelen|giden|bildirim aç|kapat` ile tercihlerini
kalıcı kaydeder. `/dydiscord admin duyuru önizle <şablon>` ve `gönder` duyuru
akışını yönetir; `duyurular duraklat <dakika>` planlı gönderimleri durdurur.
İlk eşleme ödülü, karşılama, açılış bildirimi ve çekiliş varsayılan kapalıdır.
`/dydiscord admin çekiliş başlat <saniye> <kazanan> <ödül>` çekiliş açar;
`bitir`, `iptal`, `liste` ayrı izinlerle çalışır. Çekiliş ödülünü siz teslim
edersiniz; para/site işlemi otomatik yapılmaz.
`admin ayarlar doğrula` değişiklikleri uygulamadan kontrol eder;
`admin rapor` gizli bilgiler içermeyen destek raporu üretir.
Ayrıntılar: [topluluk araçları](community-features.md), [ödüller](link-rewards.md),
[çekilişler](giveaways.md). Eski hesaplar tekrar eşleme gerektirmez.
