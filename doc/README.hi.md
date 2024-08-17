<p align="center"><a href="https://newpipe.net"><img src="../assets/new_pipe_icon_5.png" width="150"></a></p> 
<h2 align="center"><b>NewPipe</b></h2>
<h4 align="center">Android के लिए एक ओपन सोर्स, हल्का YouTube ऐप।</h4>

<p align="center"><a href="https://f-droid.org/packages/org.schabi.newpipe/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="इसे F-Droid पर पाएँ" height=80/></a></p>

<p align="center">
<a href="https://github.com/TeamNewPipe/NewPipe/releases" alt="GitHub प्रकाशन"><img src="https://img.shields.io/github/release/TeamNewPipe/NewPipe.svg" ></a>
<a href="https://www.gnu.org/licenses/gpl-3.0" alt="लाइसेंस: GPLv3"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
<a href="https://github.com/TeamNewPipe/NewPipe/actions" alt="बिल्ड की स्थिति"><img src="https://github.com/TeamNewPipe/NewPipe/workflows/CI/badge.svg?branch=dev&event=push"></a>
<a href="https://hosted.weblate.org/engage/newpipe/" alt="अनुवाद की स्थिति"><img src="https://hosted.weblate.org/widgets/newpipe/-/svg-badge.svg"></a>
<a href="https://web.libera.chat/#newpipe" alt="IRC चैनल: #newpipe"><img src="https://img.shields.io/badge/IRC%20chat-%23newpipe-brightgreen.svg"></a>
</p>
<hr>
<p align="center"><a href="#ऐप-कैसी-दिखती-है">ऐप कैसी दिखती है</a> &bull; <a href="#विवरण">विवरण</a> &bull; <a href="#सुविधाएँ">सुविधाएँ</a> &bull; <a href="#स्थापित-करना-और-अपडेट-करना">स्थापित करना और अपडेट करना</a> &bull; <a href="#योगदान-करें">योगदान करें</a> &bull; <a href="#आर्थिक-योगदान-करें">आर्थिक योगदान करें</a> &bull; <a href="#लाइसेंस">लाइसेंस</a></p>
<p align="center"><a href="https://newpipe.net">वेबसाइट</a> &bull; <a href="https://newpipe.net/blog/">ब्लॉग</a> &bull; <a href="https://newpipe.net/FAQ/">साधारण सवाल-जवाब</a> &bull; <a href="https://newpipe.net/press/">प्रेस</a></p>
<hr>

*Read this document in other languages: [Deutsch](README.de.md), [English](../README.md), [Español](README.es.md), [Français](README.fr.md), [हिन्दी](README.hi.md), [Italiano](README.it.md), [한국어](README.ko.md), [Português Brasil](README.pt_BR.md), [Polski](README.pl.md), [ਪੰਜਾਬੀ ](README.pa.md), [日本語](README.ja.md), [Română](README.ro.md), [Soomaali](README.so.md), [Türkçe](README.tr.md), [正體中文](README.zh_TW.md), [অসমীয়া](README.asm.md), [うちなーぐち](README.ryu.md), [Српски](README.sr.md)*

> [!warning]
> <b>यह एक बीटा संस्करण है, तो अगर आपको इसमें बग्स नज़र आते हैं, कृपया हमारे GitHub रिपॉज़िटरी के ज़रिए एक समस्या खोल दें।</b>
>
> <b>NewPipe या इसके किसी फोर्क को Google Play Store पर डालने पर Store के नियमों और शर्तों का उल्लंघन होता है।</b>

## ऐप कैसी दिखती है

[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/00.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png)
[<img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width=160>](../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png)
<br/><br/>
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png)
[<img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width=405>](../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png)

## विवरण

NewPipe ना ही किसी Google फ्रेमवर्क लाइब्रेरी, और ना ही YouTube के API का इस्तेमाल करता है। इस ऐप को चलाने के लिए सिर्फ वेबसाइट्स से जानकारी प्राप्त करने की ज़रूरत है, तो इस ऐप का इस्तेमाल उन डिवाइसों पर भी किया जा सकता है जिनपर Google की सेवाएँ स्थापित नहीं हैं। और NewPipe जैसे कॉपीलेफ्ट किए गए मुक्त सॉफ़्टवेयर का इस्तेमाल करने के लिए आपको YouTube खाते की ज़रूरत नहीं।

### सुविधाएँ

* वीडियो खोजें
* खाते की ज़रूरत नहीं
* वीडियो के बारे में साधारण जानकारी पाएँ
* YouTube के वीडियो देखें
* YouTube के वीडियो सुनें
* वीडियो देखते हुए दूसरे ऐप्स का इस्तेमाल करें (फ़्लोटिंग प्लेयर)
* वीडियो देखने के लिए स्ट्रीम करने का प्लेयर चुनें
* वीडियो डाउनलोड करें
* सिर्फ ऑडियो डाउनलोड करें
* Kodi में वीडियो को खोलें
* अगले/संबंधित वीडियो देखें
* YouTube को किसी विशिष्ट भाषा में खोजें
* उम्र से जुड़ी पाबंदी वाले वीडियो देखें/छिपाएँ
* चैनलों के बारे में साधारण जानकारी पाएँ
* चैनल खोजें
* किसी विशिष्ट चैनल से वीडियो देखें
* Orbot/Tor के साथ चलाएँ (मुश्किलें आ सकती हैं)
* 1080p/2K/4K में देखें
* ऐप आपके देखे गए वीडियो याद रखेगी
* चैनलों पर सदस्यता लें
* देखे गए वीडियो खोजें
* प्लेलिस्ट्स खोजें/देखें
* प्लेलिस्ट्स को सूची में जोड़कर देखें
* वीडियों को सूची में डालें
* खुदकी प्लेलिस्ट्स बनाएँ
* उपशीर्षक
* लाइवस्ट्रीम देखें
* वीडियो पे टिप्पणियाँ देखें

### उपलब्ध सेवाएँ

NewPipe पर कई सेवाएँ उपलब्ध हैं। हमारे [प्रलेख](https://teamnewpipe.github.io/documentation/) में यह बताया गया है कि ऐप और एक्सट्रैक्टर में एक नई सेवा कैसे जोड़ी जा सकती है। अगर आप कोई नई सेवा जोड़ना चाहते हैं, कृपया हमसे संपर्क करें। वर्तमान में उपलब्ध सेवाएँ हैं:

* YouTube
* SoundCloud \[बीटा\]
* media.ccc.de \[बीटा\]
* PeerTube instances \[बीटा\]
* Bandcamp \[बीटा\]

<!-- Hidden span to keep old links compatible. -->
<span id="updates"></span>

## स्थापित करना और अपडेट करना
आप इनमें से किसी एक तरीके से NewPipe को स्थापित कर सकते हैं:
 1. हमारे अपने रिपॉज़िटरी को F-Droid पर जोड़ें और उसे वहाँ से स्थापित करें। अनुदेश यहाँ हैं: https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. [GitHub प्रकाशन](https://github.com/TeamNewPipe/NewPipe/releases) से APK डाउनलोड करें और उसे स्थापित करें।
 3. F-Droid के ज़रिए अपडेट करें। यह अपडेट करने का सबसे धीमा तरीका है, क्योंकि पहले F-Droid को बदलाव पहचानने होंगे, फिर वह APK बनाएगा, उसपर हस्ताक्षर करेगा, और आखिर में उपयोगकर्ताओं को अपडेट पुश करेगा।
 4. अपने आप एक डीबग APK बनाएँ। यह अपडेट पाने का सबसे तेज़ तरीका है, मगर यह काफ़ी कठिन है, तो हम सलाह देंगे कि आप किसी दूसरे तरीके का इस्तेमाल करें।
 
ज़्यादातर उपयोगकर्ताओं के लिए पहला तरीका ठीक है। पहले और दूसरे तरीके से स्थापित APK एक-दूसरे के अनुकूल हैं, मगर तीसरे तरीके से स्थापित APK से नहीं। ऐसा इसलिए है क्योंकि पहले और दूसरे तरीके में एक ही (हमारे) हस्ताक्षर कुँजी का इस्तेमाल हुआ है, मगर तीसरे तरीके में दूसरे (F-Droid के) हस्ताक्षर कुँजी का इस्तेमाल किया जाता है। चौथे तरीके से अपना डीबग APK फ़ाइल बनाने पर कुँजी की ज़रूरत ही नहीं पड़ती। कुँजियों से यह निश्चित किया जाता है कि उपयोगकर्ता कहीं ऐप पर कोई दुर्भावनापूर्ण अपडेट तो स्थापित नहीं कर रहा।

इस दौरान अगर आप स्रोत बदलना चाहते हैं (मान लीजिए NewPipe की मूल सुविधा खराब हो गई और F-Droid पर कोई अपडेट नहीं आया है), हम इस अनुदेश का इस्तेमाल करने की सलाह देंगे:
1. सेटिंग्स > सामग्री > डेटाबेस निर्यात करें — इसके ज़रिए अपने डेटा को बैकअप कर लें ताकि आपके पास अपने देखे गए वीडियो, सदस्यताएँ और प्लेलिस्ट्स हो
2. NewPipe को डिवाइस से हटाएँ
3. नए स्रोत से APK डाउनलोड करें और उसे स्थापित करें
4. सेटिंग्स > सामग्री > डेटाबेस आयात करें — इसके ज़रिए पहले चरण के डेटा को आयात करें

## योगदान करें
चाहे आप अपने विचार जोड़ना चाहे, या अनुवाद, डिज़ाइन में बदलाव, कोड में सफ़ाई, या कोड में भारी बदलाव, सहायता ज़रूर करें।
जितने योगदान हो, ऐप उतनी ही बेहतर होती जाती है!

अगर आप योगदान करना चाहते हैं, हमारे [योगदान के दिशानिर्देश](.github/CONTRIBUTING.md) देखें।

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="अनुवाद की स्थिति" />
</a>

## आर्थिक योगदान करें
अगर आपको NewPipe पसंद है, एक छोटे-से दान से हम खुश होंगे। आप या तो बिटकॉइन भेज सकते हैं या फिर Bountysource या Liberapay से दान कर सकते हैं। NewPipe को दान करने के बारे में अधिक जानकारी के लिए कृपया हमारी [वेबसाइट](https://newpipe.net/donate) देखें।

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="../assets/liberapay_qr_code.png" alt="liberapay.com पर NewPipe को देखें" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="../assets/liberapay_donate_button.svg" alt="Liberapay के ज़रिए दान करें" height="35px"></a></td>
  </tr>
</table>

## गोपनीयता और शर्तें

NewPipe परियोजना का लक्ष्य है मीडिया वेब सेवाओं का इस्तेमाल करने के लिए एक निजी, गुमनाम अनुभव प्रदान करना,
इसलिए यह ऐप आपकी अनुमति के बिना कोई भी डेटा प्राप्त नहीं करती है। NewPipe की गोपनीयता और शर्तों में विस्तार से बताया गया है कि क्रैश रिपोर्ट भेजते समय या हमारे ब्लॉग पर टिप्पणी जोड़ते समय कौन-कौन-सी डेटा भेजी जाती है। आपको दस्तावेज़ [यहाँ](https://newpipe.net/legal/privacy/) मिल जाएगा।

## लाइसेंस
[![GNU GPLv3 का चित्र](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)  

NewPipe ओपन सोर्स सॉफ़्टवेयर है: आप इच्छानुसार इसे सुधार सकते हैं, जाँच सकते हैं, बाँट सकते हैं और इस्तेमाल कर सकते हैं। विशिष्ट रूप से, आप मुक्त सॉफ़्टवेयर संस्थान द्वारा परिभाषित [GNU साधारण सार्वजनिक लाइसेंस](https://www.gnu.org/licenses/gpl.html) के संस्करण 3 या फिर वैकल्पिक रूप से किसी नवीन संस्करण के शर्तों के अनुसार इसे दोबारा बाँट सकते हैं और/या बदल सकते हैं।
