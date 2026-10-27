# الخادم الوسيط لخُطوة / Khotwa proxy

يجعل خُطوة سريعاً وقوياً لكل المستخدمين حول العالم دون أن يربط أحد شيئاً، ويبقي مفتاحك سرياً خارج الـ APK. يدعم Gemini (موصى به، مجاني) وOpenAI وClaude، مع الصور والبثّ كلمةً كلمة.

## النشر (5 دقائق)
1. Cloudflare ← Workers & Pages ← Create ← Create Worker، الصق محتوى `worker.js` ثم Deploy وانسخ الرابط.
2. Settings ← Variables and Secrets، أضف:
   - `GEMINI_API_KEY`: مفتاح مجاني من https://aistudio.google.com/apikey
   - `APP_TOKEN`: كلمة سرية طويلة تخترعها
   - اختياري: `OPENAI_API_KEY` أو `ANTHROPIC_API_KEY` كاحتياط، و`MODEL` لتغيير النموذج.
3. GitHub ← Settings ← Secrets and variables ← Actions، أضف `PROXY_URL` و`PROXY_TOKEN` (نفس `APP_TOKEN`).
4. Actions ← Build APK ← Run workflow.

الترتيب داخل الخادم: Gemini ← OpenAI ← Claude. وعند فشل الخادم كله ينتقل التطبيق تلقائياً إلى Khotwa Cloud.

## English
Deploy `worker.js` on Cloudflare Workers, set `GEMINI_API_KEY` (free) and `APP_TOKEN`, then add GitHub secrets `PROXY_URL` and `PROXY_TOKEN`. The worker speaks OpenAI format, supports images and streaming, and falls through Gemini → OpenAI → Claude.
