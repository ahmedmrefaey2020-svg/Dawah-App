# تركيب نسخة الأندرويد (الأذان والتطبيق مقفول)

1. انسخ ملفات `native/*.java` إلى `android/app/src/main/java/<مسار الـ appId>/` وغيّر سطر `package` في أولها لـ appId بتاعك (استبدل MainActivity الحالي).
2. ضيف محتوى `native/AndroidManifest.additions.xml` في `AndroidManifest.xml` (الأذونات + service + receivers).
3. حط أذان افتراضي في `android/app/src/main/res/raw/adhan.mp3` (بيشتغل لو المستخدم لسه ماحمّلش مؤذن).
4. `npm i @capacitor/local-notifications` ثم `npx cap sync android` وابنِ الـ APK.

## مكان الملفات على الجهاز
- الموبايل: `/data/data/<appId>/files/adhan/<id>.mp3` (مساحة التطبيق الخاصة)، ونسخة في IndexedDB لتشغيلها جوه التطبيق.
- المتصفح/PWA: IndexedDB فقط.

## ملاحظات
- الكود الأصلي (Java) ماتجرّبش على جهاز أو Android SDK هنا؛ جرّبه على جهاز حقيقي: زر 🕌 ← تجربة (١٠ ثواني) وقفل التطبيق.
- جوجل بلاي بتراجع إذنَي `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` و`SCHEDULE_EXACT_ALARM`؛ جهّز تبرير (تطبيق مواقيت صلاة).
- بعض الشركات (شاومي، أوبو، سامسونج) بتحتاج المستخدم يفعّل «التشغيل التلقائي».
