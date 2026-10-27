# DyDiscord 1.2.0

**Hile kontrolü.** `/hile-kontrol <oyuncu>` (İngilizce `/cheatcheck`) oyuncunun ekranına başlık, sohbete uyarı ve bir ses gönderir; kontrol bitene kadar `cheat-check.reminder-seconds` aralıkla tekrarlar. Hesabı eşlenmişse bot ona Discord'dan (DM, kanal ya da ikisi; `bot.yml` içinde `cheat-check.notify`) `voice-channel-id` ses kanalına gelmesini söyler. DM kapalıysa `cheat-check.channel-id` kanalında etiketlenir. Eşlenmemişse oyunda Discord daveti gösterilir. `freeze: true` iken oyuncu yerinden ayrılamaz ve yalnızca `allowed-commands` komutlarını kullanabilir. Kontrol sırasında çıkarsa `quit-commands` konsoldan çalışır (örnek: `ban {player} Hile kontrolünden kaçtı`). Bitirmek: `/hile-kontrol <oyuncu> bitir`; süren kontroller: `/hile-kontrol liste`. Yetkililer her adımı oyunda ve `log-channel-id` (boşsa `plugin-logs.channel-id`) kanalında görür. İzinler: `dydiscord.cheatcheck`, `dydiscord.cheatcheck.notify`, muafiyet için `dydiscord.cheatcheck.exempt`.

**Oyuncu raporu.** `/rapor <oyuncu> <sebep>` çevrimiçi yetkililere (`dydiscord.report.notify`) iletilir ve `reports.channel-id` kanalına raporlanan oyuncunun konumuyla gönderilir; `ping-role-id` rolü etiketlenir. Bekleme süresi `reports.cooldown-seconds`.

**Girişte eşleme zorunluluğu.** `linking.require-to-join: true` iken hesabı eşlenmemiş oyuncu sunucuya giremez; atılma ekranında eşleme kodu ve Discord daveti görünür. Kodu Discord'da `/link` ya da paneldeki Eşle düğmesiyle girer. Operatörler `require-bypass-ops` ile girebilir. Bot bağlı değilse oyuncu açık bir mesajla bekletilir.

**Takma ad ve ban eşitleme.** `moderation.nickname-sync` eşlenen üyenin Discord takma adını Minecraft adı yapar (`nickname-format`, Takma Adları Yönet izni gerekir). `moderation.ban-sync`: `off`, `minecraft-to-discord`, `discord-to-minecraft` veya `both`. Discord'dan Minecraft'a ban için bot yeniden bağlanır.

**Folia.** Aynı JAR Bukkit, Spigot, Paper, Purpur ve Folia'da çalışır.

**DyHomes logları.** `plugin-logs.dyhomes.warps` ve `public-homes` açık, her ışınlanmayı yazan `teleports` kapalı gelir.

# DyDiscord 1.1.0

**Durum kartı.** `bot.yml` içindeki `status-card` ile tek bir Discord mesajı sunucunun Çevrimiçi/Çevrimdışı durumunu, oyuncu sayısını, sürümü ve (Paper ve türevlerinde) TPS'i gösterir. Mesaj `interval-seconds` (30–3600) aralıkla yerinde düzenlenir, normal kapanışta Çevrimdışı olur. Kanal konsol kanalıyla aynı olamaz.

**DyClaim ve DyRTP logları.** `plugin-logs.channel-id` ayarlanınca claim alma/kaldırma, güven değişiklikleri, devirler ve rastgele ışınlanmalar embed olarak gönderilir. `plugin-logs.dyclaim.claims`, `trust`, `transfers` ve `plugin-logs.dyrtp.teleports` ayrı ayrı kapatılabilir. Boş kanal hepsini kapatır.

**PlaceholderAPI.** `%dydiscord_linked%`, `%dydiscord_discord_name%` ve `%dydiscord_bot_connected%`; değerler bellekten gelir. Kapatmak için `config.yml` içinde `integrations.placeholderapi: false`.

**Almanca, İspanyolca, Rusça.** Minecraft ve Discord mesajları için yeni dosyalar oluşturulur. `lang: auto` istemci dilini izler; `/dyd dil de|es|ru` ve bot dili de bunları kabul eder. Bu dillerde komut kelimeleri İngilizcedir. Yeni dil tercihleri ayrı bir tabloda tutulur; 1.0.2'ye dönülürse bu oyuncular otomatik dili görür.

**MiniMessage.** Paper ve Purpur'da Minecraft mesajları `&` kodlarının yanında `<green>`, `<gradient:...>` gibi etiketler kullanabilir. Oyuncu ve Discord adları ile sohbet etiket olarak okunmaz. Spigot'ta yalnızca `&` kodları çalışır.

**Modern kartlar.** Sohbet aktarımı dışındaki tüm bot mesajları (özel yanıtlar, yardım, topluluk komutları, konsol aktarımı, kapanış bildirimi, durum kartı ve loglar) Components V2 container olarak gönderilir.

**Geliştirici API'si ve bStats.** `DyDiscordAPI`, `AccountLinkEvent` ve `AccountUnlinkEvent`. Anonim bStats istatistikleri (34918) `metrics.enabled: false` ile kapatılır. [Bu sürümün doğrulaması](release-1.1.0-verification.md).

# DyDiscord 1.0.2

Yeni kurulumlarda sohbetin iki yönü ve giriş/çıkış/ölüm bildirimleri açıktır; ayrı sohbet kanalı ayarlanmadan mesaj gönderilmez. Konsol aktarımı kapalı başlar; altındaki komut ayarı açıktır ancak rol, Administrator ve güncel kanal erişimi kontrolleri geçerlidir. Mevcut tercihler korunur. Token yalnızca bot.yml veya ortam değişkeninden girilir.

`/dyd` oyuncu komutlarının, `/dyda` yönetimin kısayoludur. Yönetim önerileri yetkiye göre gizlenir. Çekiliş kartları Container düzenindedir; ayrılmak için süreli buton onayı gerekir. Konsol hataları etkin Türkçe/İngilizce dilinde açıklanır. [Bu sürümün doğrulaması](release-1.0.2-verification.md).

`/dydiscord admin panel kanal <id>` ilk paneli bot bağlandığında otomatik gönderir.
Yenileme ve yeniden bağlantı aynı paneli kullanır. Yönetici yardımı sekizer
komutluk sayfalardadır: `/dydiscord admin yardım 2`. Discord yardımı ve `dy!`
yanıtları düzenli embed kullanır. Rütbeler Discord'daki gerçek birincil RGB
rengiyle gösterilir; gradyan rollerin ilk rengi alınır. Başlangıç/kanal/izin ve
intent hataları artık ilgili ayara yönlendirir. [Doğrulama kaydı](release-1.0.1-verification.md).

Küçük `DyDiscord-1.0.2.jar`, kütüphaneleri ilk kurulumda Spigot/Paper'ın
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
Discord botu çalıştırır. Özel hesap eşleme ve isteğe bağlı konsol kanalı sunar.
Ayrı Node.js botu veya harici veritabanı gerekmez. İngilizce ve Türkçe dahildir.

Bu sürüm 1.0.2 adını kullanır; otomatik kontroller uygulanır. Gerçek Minecraft/Discord,
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
Eşlenen hesap rolleri ve grup kuralları `bot.yml` içindeki `roles` bölümünden düzenlenir; oyun içi kural düzenleme kaldırılmıştır. Durum, önizleme, eşitleme ve onaylı temizleme komutları korunur. ASCII karşılıklar da kabul edilir.

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

1. `build/libs/DyDiscord-1.0.2.jar` dosyasını `plugins` dizinine koyup
   sunucuyu başlatın. `plain` JAR kurulmaz. Ayarlar, mesajlar ve SQLite oluşturulur.
   Yeni kurulumda bot bağlantısı açık, konsol aktarımı kapalıdır; token/sunucu ve
   ayrı kanal tanımlanana kadar işlem yapılmaz. Komut alt ayarı açıktır; yetkili rol/Administrator ve güncel erişim denetimleri korunur.
2. [Discord Developer Portal](https://discord.com/developers/applications)
   üzerinden kendi botunuzu oluşturun. `bot` ve `applications.commands` kapsamları
   ile davet edin. Bot hesabına Administrator vermeyin.
3. Yerel `plugins/DyDiscord/bot.yml` içinde token ve tırnaklı `guild-id` yazın.
   `DYDISCORD_BOT_TOKEN` ortam değişkeni dosyanın önüne geçer. Token komutla girilmez; destek raporlarına koymayın. Aynı tokeni başka bot süreciyle paylaşmayın.
4. `/dydiscord admin davet https://discord.gg/Kodunuz` ile daveti ayarlayın.
   `config.yml` içinde `server-name` değerini düzenleyin. Dosya değişikliklerinden
   sonra `/dydiscord admin yenile` kullanın. Botu daha önce kapattıysanız
   `/dydiscord admin bot aç` ile açın.
5. `/dydiscord admin panel kanal <id>` ilk paneli bağlantı hazırken otomatik gönderir.
   Gerektiğinde `/dydiscord admin panel yenile` ile aynı paneli onarın.
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
| `/discord sohbet <aç\|kapat>` | `chat on\|off` | `dydiscord.chat.preferences` |

`/dydiscord` aynı yönlendiricidir. `/link`, `/esle`, `/eşle`, `/hesap-esle`,
`/hesap-eşle` kısa yolları kaydedilir; Unicode kökler canlı sunucuda doğrulanmadı.
Komut çakışmasında `/dydiscord eşle` veya `/dydiscord:dydiscord eşle` kullanın.
Başka eklentinin komutu zorla ele geçirilmez. Davet bot kapalıyken de sadece
komutu yazana gönderilir.

Yardım kendi yardım satırı dışında bütün izinli oyuncu komutlarını, davet ve onay/iptal dahil gösterir. Konsolda
oyuncuya özel satırlar gizlenmez; “sadece oyuncular” notu taşır. Bu komutların
çalıştırılması yine oyuncu gerektirir. Oyunda `/discord dil tr` kişisel tercihi,
`/dydiscord admin dil tr` sunucunun genel dilini değiştirir; genel komut konsolda da
çalışır. Genel `en/tr` zorlamasında kişisel tercih saklanır ve zorlanan dil bildirilir.

Oyunda kod üretin; ilk satır kopyalanabilir kodu, ikinci satır tıklanabilir Discord davetini gösterir. Discord panelinin Hesap Eşle butonuna basıp açılan forma kodunuzu girin.
Yeni kurulumlarda varsayılan kod 6 rastgele, karışmayan karakterdir; ayarlanmış
özel uzunluklar korunur. Kod 5 dakika geçerlidir. Yeni kod
için 30 saniye beklenir. Oyundan çıkmak kodu iptal eder. Kodlar sunucu yeniden başladığında geçersiz
olur. Kullanım anında sahibin mevcut çevrimiçi oturumu ve yaprak izni yeniden
kontrol edilir. Eşleme izin veya OP vermez. Bir UUID ve bir Discord hesabı yalnızca
birbirine eşlenebilir. Oyundaki başarı mesajı iki hesabın adını gösterir; Discord yanıtı tek cümlede Minecraft hesabınızın eşlendiğini belirtir. Hesabım yanıtında ham ID yerine Minecraft adı, okunaklı eşleme tarihi ve varsa entegre roller yer alır. Discord adı bulunmayan eski kayıtlarda yerelleştirilmiş hesap açıklaması
kullanılır. Eşleme kaldırma özeldir. Kaldırma, varsayılan
30 saniyelik tek kullanımlık aktör/kayıt sürümü onayı ister. Bot kapalıyken yerel
durum ve kaldırma çalışabilir. Yeni kod için botun kullanılabilir olması gerekir.

`online-mode=false` sunucularda da kod üretimi ve eşleme çalışır. Offline sunucuda
hesap sahipliğini login sistemi doğrulamalı ve giriş tamamlanmadan bütün DyDiscord
komut köklerini/alias'larını engellemelidir. DyDiscord login eklentisinin API'sinden
giriş durumu sorgulamaz; çevrimiçi oturum ve yaprak iznini kontrol eder. Kimlik
modunu değiştirmek UUID'leri değiştirebilir; eski eşlemeler otomatik taşınmaz.

## Yönetim ve izinler

Aşağıdaki yollar `/dydiscord admin` veya aynı yaprak izinleri kullanan `/dyda` altındadır. İngilizce karşılıklar da kabul edilir.
Yerel konsol ve RCON yönetebilir; oyuncunun kendi hesabı oyuncu gerektirir.

| Yol | `dydiscord.admin.` sonrası izin |
| --- | --- |
| `help [page]` / `yardım [sayfa]` | erişilebilir en az bir yönetici yaprağı |
| `önek <metin>` | `prefix` |
| `bot sunucu <id>` | `bot.guild` |
| `sohbet discord-minecraft <aç\|kapat>` (Discord → Minecraft) | `chat` |
| `sohbet minecraft-discord <aç\|kapat>` (Minecraft → Discord) | `chat` |
| `sohbet tümü <aç\|kapat>` | `chat` |
| `sohbet kanal <id>` | `chat.channel` |
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
bot dili kullanılır. Konsol da ayrıca oyun dili seçilmemişse bot dilini kullanır. Sunucu dili zorunlu olsa da kişisel
tercihiniz saklanır. `/dydiscord admin dil tr` sunucunun genel dilini değiştirir.

Discord botunun dili `bot.language: auto/en/tr` ile seçilir. Auto, ortak oyun dilini kullanır; iki ayar da auto ise İngilizceye döner. Bot anahtarı,
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

## Konsol kanalı

Panel ve sohbet kanalından ayrı bir metin kanalı seçin. Kanal herkese açık olabilir; erişebilen herkes sunucu çıktısını okuyabilir. Komut çalıştırma yine açık etkinleştirme, güncel üyelik/kanal kontrolleri ve yapılandırılmış güvenilir rol veya izin verilen Discord Administrator yetkisi gerektirir. Rol listesi ve Administrator kabulü `bot.yml` içinde düzenlenir; oyun içi düzenleme komutları kaldırılmıştır. Log aktarımı ile komut çalıştırma ayrı açılır.

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

## 1.0.1 topluluk araçları

Discord tarafında oyuncu listesi, ayarlanmış adresler/bağlantılar ve sunucu bilgisi bulunur. Oyuncu `/dydiscord sohbet aç|kapat` ile yalnızca kendisine gelen Discord sohbetini açar veya gizler; Minecraft → Discord aktarımını veya olay bildirimlerini değiştirmez. Tercih kalıcıdır. Yönetici iki yönü yukarıdaki açık yön komutlarıyla ayrı ayrı veya birlikte yönetir.

Duyuru komutları, planlı duyurular ve açılış bildirimi kaldırılmıştır. Rol kuralları, konsol yetkileri ve ödül ayrıntıları YAML dosyasından düzenlenir. Eski rapor, kurulum, yapılandırma/çeviri doğrulama ve ödül önizleme komutları kaldırılmıştır; dosya değişikliklerini yenilemeyle doğrulayıp uygulayın. İlk eşleme ödülleri ve çekilişler isteğe bağlıdır. `/dyda çekiliş başlat <saniye> <kazanan> <ödül>`, `bitir`, `iptal`, `liste` korunur. Ödül teslimini sunucu sahibi yapar.

## 1.0.1 hesap mesajları ve ortak dil

`/discord eşle` ve `/eşle`, altı karakterli kodu ve ayrı bir Discord davetini
gösterir. Kodun üzerine gelince kopyalama açıklaması çıkar; tıklayınca kod
kopyalanır. Davete tıklayınca Discord açılır. Önceki varsayılan 8 karakterlik
ayar 6'ya taşınır; diğer özel uzunluklar korunur. Kaldırma onay kodu da kısadır;
onay komutuna tıklanabilir veya elle yazılabilir.

Oyun dili ayrıca seçilmemişse bot dili kullanılır. Kayıtlı kişisel tercihler ve
`lang: en/tr` seçimleri korunur. Botun `auto` seçimi oyun için seçilen ortak
dili kullanır; ikisi de auto ise İngilizce kullanılır. Bot dilini değiştirmek
ve uygulamak için `/dydiscord admin bot dil tr` kullanılabilir.

Discord'daki başarı yanıtı başlıksız tek cümledir. Hesabım ekranında Minecraft
ve Discord hesap adları, yerelleştirilmiş okunaklı tarih ve varsa atanmış entegre rol adları görünür; ham hesap ID’leri gösterilmez.
`account.show-integrated-roles: false` rol satırını gizler;
`account.time-zone: Europe/Istanbul` tarihlerin Türkiye saatinde gösterilmesini
sağlar; varsayılan saat dilimi UTC’dir. Bu seçenekler `bot.yml` içindedir ve yenilemeyle uygulanır.
Eşleme kaldırma onayı kırmızı Container içinde kırmızı **Kaldır** ve yeşil **Vazgeç**
butonlarıyla verilir; yanıt sadece hesabın sahibine görünür.

Davet ayarı boşsa bot, izin verilen bir kanalda süresiz ve kullanım sınırı
olmayan bir davet oluşturup kaydeder. Elle yazılan davet korunur. Botun uygun
kanalda **Davet Oluştur** izni olmalıdır; ayarlanan konsol kanalı seçilmez.

`/dydiscord admin yardım [sayfa]` veya `/dyda yardım [sayfa]` yalnızca numaralı yardım açar; konu yardımı kaldırılmıştır. Başlıkta önek bir kez görünür, sayfa alt satırında önek yoktur. Kısa sohbet serileri aktarılır; tekrar, gizlilik ve hız kontrolleri korunur. Konsolun ayrı çalışanı çıktıyı 250 ms aralıkla kontrol eder; güncel erişim kontrolleri sürer. Ağ ve Discord sınırları gerçek gecikmeyi etkiler.

`dydiscord.admin.alerts` izni olan çevrimiçi yöneticiler sınırlanmış kurulum/doğrulama uyarılarını alır. Altı YAML şablonunda açıklamalar ilgili ayarın altında yer alır; sahip yorumları korunur.

Güncel kaynak ve doğrulama sınırları: [1.0.1 sadeleştirme kaydı](simplify-1.0.1-verification.md). Önceki sayılar ve canlı kanıtlar yalnızca kendi artifact’lerine aittir.
