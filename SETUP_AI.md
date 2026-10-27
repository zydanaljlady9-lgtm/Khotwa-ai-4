# الذكاء الاصطناعي في خُطوة / AI setup

## للمستخدم: لا شيء
التطبيق يعمل فوراً بذكاء مدمج (**Khotwa Cloud**) دون مفتاح أو حساب أو ربط. إذا كان الهاتف يدعم **Gemini Nano** (مثل Pixel 9 فأحدث وGalaxy S25 فأحدث) يظهر في الإعدادات ← الذكاء الاصطناعي زر «تنزيل النموذج داخل الهاتف»، فيصبح خُطوة خاصاً ويعمل بلا إنترنت ويفهم الصور.

## لصاحب التطبيق: أقصى سرعة وقوة للعالم كله (موصى به قبل النشر)
الخدمات المجانية بلا مفتاح لها حدود على كل عنوان IP. لتوزيع واسع انشر الخادم الوسيط مع مفتاح Gemini مجاني:

1. خذ مفتاحاً مجانياً من https://aistudio.google.com/apikey
2. انشر `proxy/worker.js` على Cloudflare Workers (مجاني) وأضف الأسرار: `GEMINI_API_KEY` و`APP_TOKEN`.
3. في GitHub ← Settings ← Secrets ← Actions أضف `PROXY_URL` (رابط الـ Worker) و`PROXY_TOKEN` (نفس `APP_TOKEN`).
4. شغّل Build APK. يصبح المحرك الأول «Khotwa Pro» لكل المستخدمين، مع بقاء بقية المحركات احتياطاً.

الخطوات بالتفصيل في [proxy/README.md](proxy/README.md).

## مفتاح شخصي (اختياري)
الإعدادات ← الذكاء الاصطناعي ← متقدم: الصق مفتاح Gemini (`AIza`) أو Groq (`gsk_`) أو Claude (`sk-ant-`) أو OpenAI (`sk-`). يُحفظ على الهاتف فقط ويُرسل إلى الجهة المصدرة فقط.

## English summary
Users need nothing: the built-in engine chain works instantly. For worldwide scale, deploy `proxy/worker.js` with a free `GEMINI_API_KEY` and set GitHub secrets `PROXY_URL` and `PROXY_TOKEN`. Order: Khotwa Pro (proxy/user key) → Khotwa Cloud → on-device Gemini Nano → free fallbacks → offline engine.
