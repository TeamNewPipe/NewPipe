<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/newpip_material_logo.png" width="150" alt="أيقونة NewPipe Material"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>تفريعة مستقلة من NewPipe تركز على تصميم Material 3 لنظام Android.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="الرخصة: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="حالة البناء"></a>
</p>

<p align="center"><b>اقرأ هذا الملف بـ:</b> <a href="../README.md">English</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## إشعار مهم عن التفريعة

NewPipe Material هي تفريعة مستقلة من NewPipe تركز على تصميم Material 3، وسمات التطبيق، وتحسين تجربة الاستخدام.

هذا المشروع **غير تابع، وغير ممول، وغير معتمد** من مشروع NewPipe الرسمي أو TeamNewPipe أو NewPipe e.V.

تم بناء NewPipe Material على NewPipe مع الحفاظ على رخصة NewPipe الحرة، ونسب الفضل للمشروع الأصلي، وإشعارات تراخيص الطرف الثالث.

---

## ما هو NewPipe Material؟

يحافظ NewPipe Material على تجربة NewPipe الأساسية مع تحديث هوية التطبيق وواجهة المستخدم.

أهداف التفريعة الحالية:

- أسطح وحوارات وإعدادات وتبويبات وتنقل مستوحاة من Material 3
- دعم ألوان Material You الديناميكية عند توفرها
- إعدادات يدوية للون السمة مثل: الافتراضي، محايد، أخضر، أزرق، بنفسجي، برتقالي، وردي، وأحمر
- هوية تطبيق جديدة: **NewPipe Material**
- معرف تطبيق مستقل: `org.wisso.newpipematerial`
- نسخ التصحيح تثبت بشكل منفصل كـ `org.wisso.newpipematerial.debug`
- الحفاظ على سلوك NewPipe، وتوافق الاستيراد/التصدير، والخدمات المدعومة

تتجنب هذه التفريعة تغييرات السلوك عالية المخاطر في مناطق حساسة مثل التشغيل، والتنزيلات، والتشغيل في الخلفية، والتشغيل المنبثق، ومنطق Extractor، إلا عند التعامل معها كتغييرات مخصصة ومختبرة.

---

## لقطات الشاشة

### الهاتف

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="لقطة شاشة للهاتف 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="لقطة شاشة للهاتف 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="لقطة شاشة للهاتف 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="لقطة شاشة للهاتف 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="لقطة شاشة للهاتف 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="لقطة شاشة للهاتف 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="لقطة شاشة للهاتف 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="لقطة شاشة للهاتف 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="لقطة شاشة للهاتف 9"></a>
</p>

### الجهاز اللوحي

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="لقطة شاشة للجهاز اللوحي 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="لقطة شاشة للجهاز اللوحي 2"></a>
</p>

---

## الخدمات المدعومة

يرث NewPipe Material دعم NewPipe لهذه الخدمات:

- YouTube و YouTube Music
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

يعتمد دعم الخدمات على كود NewPipe و NewPipe Extractor من المشروع الأصلي.

---

## الميزات

يحافظ NewPipe Material على مجموعة ميزات NewPipe المعروفة، ومنها:

- مشاهدة الفيديوهات والبث المباشر
- التشغيل في الخلفية
- المشغل المنبثق
- القوائم المحلية
- الاشتراكات من دون تسجيل الدخول إلى حساب منصة
- مجموعات القنوات والتغذيات
- البحث والتصفح في الخدمات المدعومة
- عرض تفاصيل الفيديو والفيديوهات ذات الصلة والتعليقات عند دعمها
- تنزيل الفيديو أو الصوت أو الترجمات عند دعمها
- استيراد وتصدير بيانات التطبيق للنسخ الاحتياطي والانتقال

إضافات التفريعة المرتبطة بـ Material:

- أدوار ألوان Material 3 في مزيد من أسطح التطبيق
- شريط تنقل سفلي عند وجود خمسة تبويبات رئيسية أو أقل، مع الرجوع إلى TabLayout قابل للتمرير عند وجود تبويبات أكثر
- جعل موضع التبويبات الرئيسية السفلي هو الافتراضي للتثبيتات الجديدة أو غير المضبوطة
- دعم ألوان السمة الديناميكية واليدوية
- توضيح نسبة NewPipe Material في شاشة About
- دعم توقيع إصدارات التفريعة

---

## التثبيت

### ملف APK للإصدار

ثبّت NewPipe Material من إصدارات GitHub في هذا المستودع أو من ملفات البناء الموقعة عند توفرها.

يستخدم NewPipe Material معرف تطبيق مختلفا عن NewPipe الرسمي، لذلك يمكن تثبيته بجانب التطبيق الرسمي:

```text
NewPipe الرسمي:       org.schabi.newpipe / net.newpipe.app حسب بناء المشروع الأصلي
NewPipe Material:     org.wisso.newpipematerial
نسخة التصحيح:         org.wisso.newpipematerial.debug
```

### نقل البيانات

لا يشارك NewPipe Material بيانات التطبيق تلقائيا مع NewPipe الرسمي.

للانتقال:

1. افتح NewPipe الرسمي.
2. صدّر قاعدة البيانات من Settings > Backup and Restore.
3. ثبّت NewPipe Material.
4. استورد قاعدة البيانات المصدرة من Settings > Backup and Restore.

احتفظ دائما بنسخة احتياطية قبل استيراد البيانات بين الإصدارات أو التفريعات.

### تحذير Google Play

لا تنشر NewPipe Material أو NewPipe أو أي تفريعة من NewPipe على Google Play. يتبع هذا المشروع نفس التحذير العملي المتعلق بالتوزيع في مشروع NewPipe الأصلي.

---

## البناء من المصدر

المتطلبات:

- JDK 21
- Android SDK
- Gradle wrapper الموجود في هذا المستودع

أوامر تحقق مفيدة:

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

بناء APK للتصحيح:

```bash
./gradlew assembleDebug -DskipFormatKtlint
```

تستخدم نسخة التصحيح اسم التطبيق **NewPipe Material Debug** والحزمة `org.wisso.newpipematerial.debug`.

---

## توقيع الإصدار

يتم إعداد توقيع الإصدار عبر متغيرات البيئة التالية:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

عند توفر القيم الأربع كلها، يستخدم بناء الإصدار مفتاح التوقيع المحدد. إذا كانت القيم غير موجودة، فلن يتم تطبيق إعداد توقيع الإصدار.

تحقق مقترح للإصدار:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## حالة التطوير

NewPipe Material تفريعة نشطة في مرحلة تحسين Material 3 وتجهيز المنتج.

المناطق المكتملة أو قيد العمل:

- اسم التطبيق ومعرف التطبيق
- فصل هوية التصحيح والإصدار
- ألوان Material 3
- التعامل مع ألوان السمة الديناميكية واليدوية
- شريط التنقل السفلي وتحسين التبويبات الرئيسية
- نسب التفريعة في شاشة About
- تحسين الحوارات، والـ snackbar، والإعدادات، وتفاصيل الفيديو، وواجهة التنزيل
- دعم سير عمل توقيع الإصدار

مناطق مؤجلة أو عالية المخاطر:

- إعادة تصميم طبقة التحكم في المشغل الرئيسي
- ألوان شريط التقدم وطبقات الإيماءات
- عناصر التحكم في قائمة الانتظار
- سلوك قوائم الجودة والصوت والترجمات
- تغييرات السلوك الواسعة في التشغيل أو التنزيل

هذه المناطق تحتاج إلى اختبار مخصص قبل أي تغييرات مرئية أو سلوكية.

---

## المساهمة

المساهمات مرحب بها، خصوصا تحسينات Material 3 المركزة، وإصلاحات الأخطاء، ونتائج الاختبار، والتوثيق، وتجهيزات الإصدار.

يرجى إبقاء التغييرات مركزة وقابلة للاختبار. في تغييرات الواجهة، أرفق لقطات قبل/بعد عند الإمكان، وتحقق من السمات Light و Dark و Black و Follow system وسمة يدوية واحدة على الأقل.

أوامر مفيدة قبل فتح Pull Request:

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

---

## مشروع NewPipe الأصلي

يعتمد NewPipe Material على NewPipe.

روابط المشروع الأصلي:

- مستودع NewPipe: https://github.com/TeamNewPipe/NewPipe
- موقع NewPipe: https://newpipe.net
- أسئلة NewPipe الشائعة: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

يرجى الإبلاغ عن المشاكل بعناية:

- مشاكل التصميم أو الهوية أو الإصدار أو Material 3 الخاصة بالتفريعة تخص هذا المستودع.
- أعطال الخدمات أو Extractor القادمة من المشروع الأصلي قد تحتاج أيضا إلى التحقق منها في NewPipe الرسمي.

---

## التبرع

إذا كنت تريد دعم NewPipe الأصلي، فراجع صفحة التبرع الرسمية:

https://newpipe.net/donate

NewPipe Material تفريعة مستقلة؛ التبرعات للمشروع الأصلي تذهب إلى مشروع NewPipe الأصلي، ولا تذهب تلقائيا إلى هذه التفريعة.

---

## الرخصة

NewPipe Material برنامج حر مبني على NewPipe ويتم توزيعه تحت رخصة GNU General Public License version 3 أو أي إصدار أحدث.

راجع ملفات الرخصة في المستودع وشاشة التراخيص داخل التطبيق للحصول على تفاصيل الرخصة وإشعارات الطرف الثالث كاملة.
