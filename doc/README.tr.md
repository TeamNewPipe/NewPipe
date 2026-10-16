<p align="center"><a href="https://newpipe.net"><img src="assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">A libre lightweight streaming front-end for Android.</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" width=206/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub NewPipe releases"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://github.com/TeamNewPipe/NewPipe-nightly/releases" alt="GitHub NewPipe nightly releases"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe-nightly.svg?labelColor=purple&label=dev%20nightly"></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="License: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="Build Status"><img src="https://github.com/TeamNewPipe/NewPipe/actions/workflows/ci.yml/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="Translation Status"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
</p>

<p align="center">
<a href="https://web.libera.chat/#newpipe" alt="IRC channel: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
<a href="https://matrix.to/#/#newpipe:matrix.newpipe-ev.de" alt="Matrix channel: #newpipe"><img src="https://img.shields.io/badge/Matrix%20chat-%23newpipe-blue"></a>
</p>

<hr>
<p align="center"><a href="#screenshots">Screenshots</a> &bull; <a href="#supported-services">Supported Services</a> &bull; <a href="#description">Description</a> &bull; <a href="#features">Features</a> &bull; <a href="#installation-and-updates">Installation and updates</a> &bull; <a href="#contribution">Contribution</a> &bull; <a href="#donate">Donate</a> &bull; <a href="#license">License</a></p>
<p align="center"><a href="https://newpipe.net">Website</a> &bull; <a href="https://newpipe.net/blog/">Blog</a> &bull; <a href="https://newpipe.net/FAQ/">FAQ</a> &bull; <a href="https://newpipe.net/press/">Press</a></p>
<hr>

*Read this document in other languages: [Deutsch](doc/README.de.md),
[English](README.md), [Español](doc/README.es.md), [Français](doc/README.fr.md),
[हिन्दी](doc/README.hi.md), [Italiano](doc/README.it.md),
[한국어](doc/README.ko.md), [Português Brasil](doc/README.pt_BR.md),
[Polski](doc/README.pl.md), [ਪੰਜਾਬੀ ](doc/README.pa.md),
[日本語](doc/README.ja.md), [Română](doc/README.ro.md),
[Soomaali](doc/README.so.md), [Türkçe](doc/README.tr.md),
[正體中文](doc/README.zh_TW.md), [অসমীয়া](doc/README.asm.md),
[うちなーぐち](doc/README.sr.md), [Српски](README.ar.md) , [العربية]{19}*

> [!warning]\
> <b>BU SÜRÜM BETA SÜRÜMÜDÜR, BU NEDENLE HATALARLA KARŞILAŞABİLİRSİNİZ. HATA
> BULURSANIZ BU GITHUB DEPOSUNDA BUNU BİLDİRİN.</b>
> 
> <b>GOOGLE PLAY STORE'A NEWPIPE VEYA BAŞKA BİR KOPYASINI KOYMAK, PLAY STORE
> ŞARTLARINI VE KOŞULLARINI İHLAL EDER.</b>
> 
> <b>“NewPipe”, [TİCARİ MARKA İLKESİ](https://newpipe-ev.de/policy/trademark/)
> KAPSAMINDA TESCİLLİ BİR SÖZCÜK MARKASIDIR. HAK İHLALİ OLUŞTURAN KULLANIMLARA
> MÜSAADE EDİLMEYECEKTİR.</b>

## Ekran görüntüleri

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/00.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/01.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/02.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/03.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/04.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/05.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/06.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/07.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/08.png)
<br/><br/>\
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png)
[<img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width=405>](fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png)

### Desteklenen Hizmetler

NewPipe şu anda şu hizmetleri destekliyor:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([web sitesi](https://www.youtube.com/)) ve YouTube Müzik ([web
  sitesi](https://music.youtube.com/))
  ([wiki](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([web sitesi](https://joinpeertube.org/)) ve tüm örnekleri (Ne anlama
  geldiğini öğrenmek için web sitesini açın!)
  ([wiki](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([web sitesi](https://bandcamp.com/))
  ([wiki](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([web sitesi](https://soundcloud.com/))
  ([wiki](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([web sitesi](https://media.ccc.de/))
  ([wiki](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

Gördüğünüz gibi, NewPipe birçok video ve ses hizmetini destekliyor. Her ne kadar
YouTube ile başlamış olsa da, yıllar içinde kullanıcılar başka hizmetler de
ekleyerek NewPipe'ı giderek daha çok yönlü hale getirdiler!

Kısmen koşullar nedeniyle, kısmen de popülerliği sayesinde YouTube, bu hizmetler
arasında en iyi desteklenenidir. Diğer hizmetlerden herhangi birini
kullanıyorsanız veya bunları biliyorsanız, lütfen bu hizmetlere yönelik
desteğimizi geliştirmemize yardımcı olun! SoundCloud ve PeerTube için bakım
sorumluları arıyoruz.

Yeni bir hizmet eklemeyi düşünüyorsanız, lütfen önce bizimle iletişime geçin!
[Belgelerimiz](https://teamnewpipe.github.io/documentation/), uygulama ve
[NewPipe Çıkarıcı](https://github.com/TeamNewPipe/NewPipeExtractor) için yeni
bir hizmetin nasıl eklenebileceği konusunda daha fazla bilgi sağlar.

## Açıklama

NewPipe herhangi bir Google çerçeve kütüphanesini, ya da YouTube API
hizmetlerini kullanmaz. Gerekli web hizmetleri yalnızca gerekli bilgileri almak
için kaynak olarak kullanılır, bu nedenle bu uygulama Google hizmetleri yüklü
olmayan cihazlarda da kullanılabilir. Ayrıca, copyleft özgür yazılımı olan
NewPipe'ı kullanmak için bir YouTube hesabına ihtiyacınız yoktur.

Ayrıca, bunlar özgür ve açık kaynaklı yazılımlar olduğu için, ne uygulama ne de
Çıkarıcı, Google Play Hizmetleri gibi özgür olmayan kütüphaneler veya çerçeveler
kullanmaz. Bu, Google uygulamalarının kurulu olmadığı aygıtlarda veya özel
ROM’larda NewPipe'ı kullanabileceğiniz anlamına gelir.

### Özellikler

* Video arama
* Videolar hakkında genel bilgileri görüntüleme
* YouTube videoları izleme
* YouTube videolarını dinleme
* Pop-up modu (hareketli oynatıcı)
* Video izlemek için akış oynatıcısını seçme
* Video indirme
* Sadece ses indirme
* Videoyu Kodi'de açma
* Sonraki video/ilgili videolar
* YouTube'u belirli bir dilde arayın
* Yaş sınırlı içeriği izleme/engelleme
* Kanallar hakkındaki genel bilgileri görüntüleme
* Kanal arama
* Bir kanaldaki videoları izleme
* Orbot/Tor desteği (henüz direkt olarak değil)
* 1080p/2K/4K desteği
* Geçmişi görme
* Kanallara abone olma
* Geçmişte arama
* Oynatma listesi arama/oynatma
* Çalma listelerini sıralayıp oynatın

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## Kurulum ve güncellemeler
Aşağıdaki yöntemlerden birini kullanarak NewPipe'ı kurabilirsiniz:
 1. Özel depomuzu F-Droid'e ekleyin ve oradan yükleyin. Kılavuzu şurada
    bulabilirsiniz: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. APK'yı [GitHub
    sürümlerinden](https://github.com/TeamNewPipe/NewPipe/releases) indirin ve
    kurun.
 3. F-Droid ile güncelleyin. Bu, güncellemeleri almanın en yavaş yöntemidir,
    çünkü F-Droid değişiklikleri tanımalı, APK'yı kendisi oluşturmalı,
    imzalamalı ve ardından güncellemeyi kullanıcılara dağıtmalıdır.
 4. Kendiniz bir APK derleyin. Bu yöntem, cihazınızda yeni özellikler edinmenin
    en hızlı yoludur, ancak çok daha karmaşıktır, bu nedenle diğer yöntemlerden
    birini kullanmanızı öneririz.
 5. Verilerinizi yedekleyin. `NewPipe Ayarları > İçerik > Veritabanını dışa
    aktar` seçeneklerini izleyerek aboneliklerinizi, oynatma listelerinizi ve
    geçmişinizi yedekleyin.

Çoğu kullanıcı için yöntem 1'i öneririz. Yöntem 1 veya 2 kullanılarak yüklenen
APK'lar birbiriyle uyumludur, ancak yöntem 3 kullanılarak yüklenenlerle uyumlu
değildir. Bu durum, 1 ve 2 için kullanılan aynı imzalama anahtarıın (bizim
anahtarımız) 3 için kullanılan imzalama anahtarından (F-Droid'in anahtarı)
farklı olmasından kaynaklanmaktadır. Yöntem 4 kullanılarak oluşturulan deneysel
APK'larda anahtar yoktur. İmzalama anahtarları, bir kullanıcının bir uygulamaya
kötü amaçlı bir güncelleme yüklemek için kandırılmadığından emin olmanıza
yardımcı olur.

Bu arada, herhangi bir nedenle kaynakları değiştirmek istiyorsanız (örneğin,
NewPipe'ın temel bir işlevi bozuldu ve F-Droid tarafında henüz bir güncelleme
yayınlanmadı), bu prosedürü izlemenizi öneririz:
1. NewPipe'ı kaldırın
2. APK dosyasını yeni bir kaynaktan indirin ve yükleyin
3. `Ayarlar > İçerik > Veritabanını içe aktar` seçeneklerini izleyerek 1.
   adımdaki verileri içe aktarın
4. Ayarlar > Yedekle ve Geri Yükle > Veri Tabanını İçe Aktar seçeneği üzerinden
   1. adımdaki verileri içe aktarın

> [!Not] Resmi uygulamaya bir veri tabanı içe aktarırken, bunun her zaman resmi
> uygulamadan _dışa aktarılan_ veri tabanı olduğundan emin olun. Resmi uygulama
> dışındaki bir APK'dan dışa aktarılan bir veri tabanını içe aktarırsanız, bu
> durum sorunlara yol açabilir. Bu tür bir işlem desteklenmez ve yalnızca ne
> yaptığınızı tam olarak bildiğinizden kesinlikle emin olduğunuzda bunu
> yapmalısınız.

### APK Bilgileri

Bu, tarafımızca imzalanan indirilen APK'ları doğrulamak için kullanılan NewPipe
imza anahtarının SHA parmak izidir. Parmak izi, [NewPipe web
sitesinde](https://newpipe.net#download) de bulunabilir. Bu, yöntem 2 için
geçerlidir.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## Katkıda bulunma
Fikirleriniz, çevirileriniz, tasarım değişiklikleriniz, kod temizlemeniz veya
ağır kod değişiklikleriniz olsun, yardımınıza her zaman açığız. Yapılan her
değişiklikle NewPipe daha da iyi bir konuma geliyor!

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## Bağış
NewPipe'ı beğendiyseniz, yapacağınız bağışlar bizi motive eder. Bitcoin
gönderebilir veya Bountysource veya Liberapay aracılığıyla bağış yapabilirsiniz.
NewPipe'a bağış yapma hakkında daha fazla bilgi için lütfen [web
sitemizi](https://newpipe.net/donate) ziyaret edin.

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## Gizlilik politikası

NewPipe projesi, çevrimiçi akış hizmetlerini kullanmak için özel, özgür ve
anonim bir deneyim sunmayı amaçlamaktadır. Bu doğrultuda, uygulama sizin izniniz
olmadan herhangi bir veri toplamaz. NewPipe'ın Gizlilik Politikası, bir çökme
raporu gönderdiğinizde veya blogumuzda yorum yaptığınızda hangi verilerin
gönderildiğini ve saklandığını ayrıntılı olarak açıklar. İlgili belgeyi
[burada](https://newpipe.net/legal/privacy/) bulabilirsiniz.

## Lisans
[![GNU GPLv3
Resmi](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe özgür bir yazılımdır. Kendi başınıza kullanabilir, öğrenebilir,
paylaşabilir ve geliştirebilirsiniz. Free Software Foundation tarafından
yayınlanan GNU Genel Kamu Lisansı, Lisansın 3. sürümü veya (isteğe bağlı olarak)
daha sonraki bir sürümü şartları ve koşulları altında yeniden dağıtabilir
ve/veya değiştirebilirsiniz.
