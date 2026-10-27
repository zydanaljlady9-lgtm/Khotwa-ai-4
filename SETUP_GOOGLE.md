# تفعيل تسجيل الدخول بـ Google (مرة واحدة، حوالي 10 دقائق)

التطبيق جاهز برمجياً، لكن Google لا تسمح بالدخول إلا بعد ربط التطبيق بمشروعك. لا أستطيع إنشاء الحساب نيابةً عنك، فهذه الخطوات منك أنت:

## 1) أنشئ مشروعاً
1. افتح https://console.cloud.google.com وسجّل بحسابك.
2. من القائمة العلوية: New Project ← الاسم `Khotwa` ← Create.

## 2) شاشة الموافقة
APIs & Services ← OAuth consent screen ← External ← الاسم `خُطوة AI` ← بريدك ← Save.
أضف نفسك (وأي شخص سيجرّب العرض) في Test users ما دام التطبيق في وضع Testing.

## 3) أنشئ معرّفين (Credentials ← Create Credentials ← OAuth client ID)
**أ) Android**
- Package name: `com.khotwa.guide`
- SHA-1: `F9:F7:F6:D6:F5:69:75:75:FB:B8:7B:34:B6:03:D8:A7:B3:AF:A9:9E`

**ب) Web application** (هذا هو المهم للتطبيق)
- أي اسم، واترك الروابط فارغة ← Create ← انسخ **Client ID** (ينتهي بـ `.apps.googleusercontent.com`).

## 4) أعطني المعرّف أو ضعه بنفسك
المعرّف ليس سرّاً. الخياران:
- أرسله لي هنا وأضعه وأبني النسخة، أو
- في GitHub: Settings ← Secrets and variables ← Actions ← New repository secret باسم `GOOGLE_WEB_CLIENT_ID`، ثم شغّل Build APK.

بعدها يعمل زر «المتابعة باستخدام Google» مباشرة.

> ملاحظة: بصمة SHA-1 أعلاه لمفتاح التوقيع الموجود في المستودع (`app/debug.keystore`). إن غيّرت المفتاح لاحقاً فغيّر البصمة في Google Cloud أيضاً.
