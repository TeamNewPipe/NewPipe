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

> [!تحذير]\
> <b>هذا التطبيق في مرحلة تجريبية، لذا قد تواجه بعض الأخطاء. إذا حدث ذلك، افتح
> بلاغًا في مستودعنا على GitHub من خلال ملء قالب البلاغ.</b>
> 
> <b>وضع NewPipe، أو أي نسخة متفرعة منه، في متجر Google Play يخالف الشروط
> والأحكام الخاصة بهم.</b>
> 
> <b>"NewPipe" علامة تجارية مسجّلة تخضع [لسياسة العلامات التجارية الخاصة
> بها](https://newpipe-ev.de/policy/trademark/). لن يتم التهاون مع التطبيقات
> المخالفة.</b>

## لقطات الشاشة

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

### الخدمات المدعومة

NewPipe حاليًا يدعم هذه الخدمات:

<!-- We link to the service websites separately to avoid people accidentally opening a website they didn't want to. -->
* YouTube ([موقع إلكتروني](https://www.youtube.com/)) و YouTube Music ([موقع
  إلكتروني](https://music.youtube.com/))
  ([ويكي](https://en.wikipedia.org/wiki/YouTube))
* PeerTube ([موقع إلكتروني](https://joinpeertube.org/)) و جميع حالاته (قم بزيارة
  الموقع لتعرف ماذا يعني ذلك!) ([ويكي](https://en.wikipedia.org/wiki/PeerTube))
* Bandcamp ([موقع إلكتروني](https://bandcamp.com/))
  ([ويكي](https://en.wikipedia.org/wiki/Bandcamp))
* SoundCloud ([موقع إلكتروني](https://soundcloud.com/))
  ([ويكي](https://en.wikipedia.org/wiki/SoundCloud))
* media.ccc.de ([موقع إلكتروني](https://media.ccc.de/))
  ([ويكي](https://en.wikipedia.org/wiki/Chaos_Computer_Club))

كما يمكنكم ملاحظة، يدعم نيوبايب العديد من خدمات الفيديو والصوت. على الرغم من أن
البداية كانت مع يوتيوب، إلا أن أشخاصًا آخرين قاموا بإضافة خدمات جديدة على مر
السنين، مما جعل نيوبايب أكثر تنوعًا ومرونة!

جزئيًا بسبب الظروف، وجزئيًا بسبب شعبيته، يوتيوب هو الخدمة الأفضل دعمًا من بين
هذه الخدمات. إذا كنت تستخدم أو تعرف أيًا من هذه الخدمات الأخرى، فالرجاء مساعدتنا
في تحسين الدعم لها! نحن بحاجة إلى من يعتنون بساوندكلاود وبييرتيوب.

إذا كان لديك نية في إضافة خدمة جديدة، يرجى التواصل معنا أولا! توفر [مستنداتنا]
(https://teamnewpipe.github.io/documentation/) المزيد من المعلومات حول كيفية
إضافة خدمة جديدة إلى التطبيق وإلى [NewPipe Extractor]
(https://github.com/TeamNewPipe/NewPipeExtractor)..

## وصف

نيوبايب يعمل عن طريق جلب البيانات المطلوبة من واجهة برمجة التطبيقات الرسمية (مثل
بيرتيوب) للخدمة التي تستخدمها. إذا كانت واجهة برمجة التطبيقات الرسمية مقيدة (مثل
يوتيوب) لأغراضنا، أو مملوكة، يقوم التطبيق بتحليل الموقع الإلكتروني أو استخدام
واجهة برمجة التطبيقات الداخلية بدلاً من ذلك. وهذا يعني أنك لا تحتاج إلى حساب على
أي خدمة لاستخدام نيوبايب.

أيضًا، نظرًا لأنهما برمجيات حرة ومفتوحة المصدر، فإن التطبيق ولا المستخرج
يستخدمان أي مكتبات أو إطارات برمجية مملوكة، مثل خدمات جوجل بلاي. وهذا يعني أنه
يمكنك استخدام نيوبايب على الأجهزة أو الرومات المخصصة التي لا تحتوي على تطبيقات
جوجل المثبتة.

### سمات

* 4K شاهد مقاطع الفيديو بدقة تصل إلى
* الاستماع إلى الصوت في الخلفية، وتحميل دفق الصوت فقط لحفظ البيانات
* الوضع المنبثق (المشغل العائم، المعروف أيضًا باسم Picture-in-Picture)
* مشاهدة البث المباشر
* إظهار/إخفاء الترجمة
* ابحث عن مقاطع فيديو وصوت (على اليوتيوب، يمكنك أيضًا تحديد لغة المحتوى)
* وضع مقاطع الفيديو في قائمة الانتظار (وحفظها اختياريًا كقوائم تشغيل محلية)
* إظهار/إخفاء المعلومات العامة حول مقاطع الفيديو (مثل الوصف والعلامات)
* إظهار/إخفاء مقاطع الفيديو التالية أو ذات الصلة
* إظهار/إخفاء التعليقات
* ابحث عن مقاطع الفيديو والصوت والقنوات وقوائم التشغيل والألبومات
* تصفح مقاطع الفيديو والتسجيلات الصوتية للقناة
* الاشتراك في القنوات (نعم، دون الحاجة إلى تسجيل الدخول إلى حساب!)
* احصل على إشعارات حول الفيديوهات الجديدة من القنوات التي اشتركت فيها
* إنشاء مجموعات القنوات وتحريرها (لتسهيل الإدارة والتنقل)
* تصفح خلاصات الفيديو التي تم إنشاؤها من مجموعات القنوات الخاصة بك
* عرض وبحث تاريخ العرض الخاص بك
* عرض قوائم التشغيل والبحث فيها (هذه قوائم تشغيل عن بعد، أي أنه يتم سحبها من
  الخدمة التي تتصفحها)
* إنشاء وتحرير قوائم التشغيل المحلية (يتم إنشاؤها وحفظها داخل التطبيق، وليس لها
  أي علاقة بأي من الخدمات)
* تنزيل مقاطع الفيديو/الصوت/الترجمات
* العب في كودي
* مشاهدة/حظر المحتوى المقيد بالفئة العمرية

<!-- Hidden span to keep old links compatible. You should remove this span if you're translating the README into another language.-->
<span id="updates"></span>

## التثبيت والتحديثات
يمكنك تثبيت نيوبايب باستخدام إحدى الطرق التالية:
 1. أضف مستودعنا في F-Droid وقم بتثبيت التطبيق من هناك. الإرشادات متوفرة هنا:
    https://newpipe.net/FAQ/tutorials/install-add-fdroid-repo/
 2. قم بتنزيل APK من [إصدارات
    GitHub](https://github.com/TeamNewPipe/NewPipe/releases) وقم بتثبيته.
 3. قم بتحديث التطبيق عبر F-Droid. هذه الطريقة هي الأبطأ للحصول على التحديثات،
    حيث يجب على F-Droid اكتشاف التغييرات، وبناء ملف APK بنفسه، وتوقيعه، ثم نشر
    التحديث أخيرًا.
 4. قم بإنشاء ملف APK للتصحيح بنفسك. هذه هي الطريقة الأسرع للحصول على ميزات
    جديدة على جهازك، لكنها أكثر تعقيدًا بكثير، ولهذا نوصي باستخدام أحد الطرق
    الأخرى.
 5. ذا كنت مهتمًا بميزة معينة أو إصلاح خلل محدد في طلب سحب (PR) من هذا المستودع،
    يمكنك أيضًا تنزيل ملف APK من هذا الطلب. ستجد التعليمات في وصف الطلب. الرائع
    في ملفات APK الخاصة بالطلبات السحب هو أنها تُثبت بجانب التطبيق الرسمي، لذا
    لا داعي للقلق بشأن فقدان بياناتك أو تعطيل أي شيء.

نوصي بالطريقة الأولى لمعظم المستخدمين. يتوافق ملفات APK التي تم تثبيتها باستخدام
الطرق الأولى أو الثانية مع بعضها البعض (مما يعني أنه إذا قمت بتثبيت NewPipe
باستخدام إحدى هاتين الطريقتين، يمكنك أيضًا تحديث NewPipe باستخدام الطريقة
الأخرى)، ولكن لا يتوافق ذلك مع تلك التي تم تثبيتها باستخدام الطريقة الثالثة.
يرجع ذلك إلى استخدام نفس مفتاح التوقيع (الخاص بنا) للطرق الأولى والثانية، بينما
يتم استخدام مفتاح مختلف (مفتاح F-Droid) للطريقة الثالثة. إن بناء ملف APK للتصحيح
باستخدام الطريقة الرابعة لا يتطلب أي مفتاح. تساعد مفاتيح التوقيع في التأكد من
عدم خداع المستخدم وتثبيت تحديث غير مشروع للتطبيق. عند استخدام الطريقة الخامسة،
يتم توقيع كل ملف APK بمفتاح توقيع فريد وعشوائي يتم إنشاؤه بواسطة GitHub Actions؛
لذا لا يمكنك تحديثها. فيجب عليك حفظ واستعادة بيانات التطبيق في كل مرة ترغب في
استخدام ملف APK جديد.

في الوقت الحاضر، إذا كنت ترغب في تغيير المصدر لأي سبب من الأسباب (على سبيل
المثال، إذا كانت ميزة NewPipe الأساسية معطلة ولم يتم تحديثها بعد على F-Droid)،
نوصي باتباع هذه الإجراءات:
1. قم بعمل نسخة احتياطية من بياناتك عبر الإعدادات > النسخ الاحتياطي والاستعادة >
   تصدير قاعدة البيانات للحفاظ على السجل، والاشتراكات، وقوائم التشغيل
2. إلغاء تثبيت NewPipe
3. نزّل ملف APK من المصدر الجديد وثبّته
4. استورد البيانات من الخطوة 1 عبر الإعدادات > النسخ الاحتياطي والاستعادة >
   استيراد قاعدة البيانات

> [!ملاحظة] عند استيراد قاعدة بيانات إلى التطبيق الرسمي، تأكد دائمًا من أنها تلك
> التي صدّرتها _من_ التطبيق الرسمي. إذا استوردت قاعدة بيانات تم تصديرها من APK
> غير التطبيق الرسمي، فقد يؤدي ذلك إلى إفساد الأمور. هذا الإجراء غير مدعوم، ويجب
> ألا تقوم به إلا عندما تكون متأكدًا تمامًا أنك تعرف ما تفعله.

### معلومات APK

هذا هو بصمة SHA لمفتاح توقيع NewPipe للتحقق من ملفات APK التي تم تنزيلها
والموقّعة من قِبلنا. البصمة متاحة أيضًا على [موقع
NewPipe](https://newpipe.net#download). هذا ذو صلة بالطريقة 2.
```
CB:84:06:9B:D6:81:16:BA:FA:E5:EE:4E:E5:B0:8A:56:7A:A6:D8:98:40:4E:7C:B1:2F:9E:75:6D:F5:CF:5C:AB
```

## مساهمة
سواء كانت لديك أفكار، أو ترجمات، أو تغييرات في التصميم، أو تنظيف التعليمات
البرمجية، أو حتى تغيير كبير في التعليمات البرمجية، فنحن نرحب بأي مساعدة. التطبيق
يصبح أفضل قليلا مع كل مساهمة، مهما كانت كبيرة أو صغيرة! إذا كنت ترغب في
المشاركة، قم بإلقاء نظرة على [ملاحظات المساهمة](.github/CONTRIBUTING.md).

<a href="https://hosted.weblate.org/engage/newpipe/">
<img src="https://hosted.weblate.org/widgets/newpipe/-/287x66-grey.png" alt="Translation status" />
</a>

## التبرعات
إذا كنت تحب NewPipe، فنحن نرحب بك لإرسال التبرع. نحن نفضل Liberapay لأنه مفتوح
المصدر وغير ربحي. لمزيد من المعلومات حول كيفية التبرع لـ NewPipe، تفضل بزيارة
[موقعنا الإلكتروني](https://newpipe.net/donate).

<table>
  <tr>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="https://upload.wikimedia.org/wikipedia/commons/2/27/Liberapay_logo_v2_white-on-yellow.svg" alt="Liberapay" width="80px" ></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/"><img src="assets/liberapay_qr_code.png" alt="Visit NewPipe at liberapay.com" width="100px"></a></td>
    <td><a href="https://liberapay.com/TeamNewPipe/donate"><img src="assets/liberapay_donate_button.svg" alt="Donate via Liberapay" height="35px"></a></td>
  </tr>
</table>

## سياسة الخصوصية

يهدف مشروع NewPipe إلى توفير تجربة خاصة ومجهولة لاستخدام خدمات الوسائط المستندة
إلى الويب. ولذلك، فإن التطبيق لا يجمع أي بيانات دون موافقتك. تشرح سياسة خصوصية
NewPipe بالتفصيل البيانات التي يتم إرسالها وتخزينها عند إرسال تقرير بالحادث أو
ترك تعليق على مدونتنا. يمكنك العثور على المستند
[هنا](https://newpipe.net/legal/privacy/).

## رخصة
[![GNU GPLv3
Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

NewPipe هو برنامج مجاني: يمكنك استخدامه ودراسته ومشاركته وتحسينه كما يحلو لك.
وبشكل أكثر تحديدًا، يمكنك إعادة توزيعه و/أو تعديله بموجب شروط [رخصة GNU
العامة](https://www.gnu.org/licenses/gpl.html) كما نشرتها مؤسسة البرمجيات الحرة،
أيًا من الإصدارين. 3 من الترخيص، أو (حسب اختيارك) أي إصدار لاحق.
