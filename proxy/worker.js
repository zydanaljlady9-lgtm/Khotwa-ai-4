// Khotwa AI proxy — Cloudflare Worker.
// يخفي مفتاحك عن التطبيق، ويبثّ الرد كلمةً كلمة بصيغة OpenAI، ويدعم الصور.
// The app sends an OpenAI-style request; this worker routes it to Gemini, OpenAI or Claude
// and always answers in OpenAI format (streaming or not).
//
// Variables (Cloudflare > Worker > Settings > Variables and Secrets):
//   GEMINI_API_KEY     (Secret)  free key from https://aistudio.google.com/apikey  — recommended
//   OPENAI_API_KEY     (Secret)  optional
//   ANTHROPIC_API_KEY  (Secret)  optional
//   APP_TOKEN          (Secret)  long random string; same value as GitHub secret PROXY_TOKEN
//   MODEL              (optional) override the model name of the chosen provider
// Provider order: Gemini > OpenAI > Claude (the first key found is used; on failure the next is tried).

const MAX_BODY = 6_000_000; // يكفي لصورة شاشة مضغوطة
const MAX_TOKENS = 1200;

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

function anthropicToOpenAIStream(body) {
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  let buf = "";
  return body.pipeThrough(new TransformStream({
    transform(chunk, ctl) {
      buf += dec.decode(chunk, { stream: true });
      let idx;
      while ((idx = buf.indexOf("\n")) >= 0) {
        const line = buf.slice(0, idx).trim();
        buf = buf.slice(idx + 1);
        if (!line.startsWith("data:")) continue;
        try {
          const j = JSON.parse(line.slice(5).trim());
          if (j.type === "content_block_delta" && j.delta && j.delta.type === "text_delta") {
            ctl.enqueue(enc.encode("data: " + JSON.stringify({ choices: [{ delta: { content: j.delta.text } }] }) + "\n\n"));
          } else if (j.type === "message_stop") {
            ctl.enqueue(enc.encode("data: [DONE]\n\n"));
          }
        } catch (_) { /* ignore */ }
      }
    },
  }));
}

// OpenAI content (string or parts) -> Anthropic content blocks
function toAnthropicContent(content) {
  if (typeof content === "string") return [{ type: "text", text: content }];
  const out = [];
  for (const p of Array.isArray(content) ? content : []) {
    if (p.type === "text") out.push({ type: "text", text: String(p.text || "") });
    else if (p.type === "image_url") {
      const url = typeof p.image_url === "string" ? p.image_url : p.image_url && p.image_url.url;
      const m = /^data:(image\/[a-z]+);base64,(.+)$/i.exec(url || "");
      if (m) out.push({ type: "image", source: { type: "base64", media_type: m[1], data: m[2] } });
    }
  }
  return out.length ? out : [{ type: "text", text: "" }];
}

async function openAICompatible(url, key, model, body) {
  return fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer " + key },
    body: JSON.stringify({ ...body, model }),
  });
}

async function viaClaude(env, body, stream) {
  const msgs = body.messages;
  const system = msgs.filter(m => m.role === "system").map(m => typeof m.content === "string" ? m.content : "").join("\n");
  const merged = [];
  for (const m of msgs) {
    if (m.role !== "user" && m.role !== "assistant") continue;
    const blocks = toAnthropicContent(m.content);
    const last = merged[merged.length - 1];
    if (last && last.role === m.role) last.content.push(...blocks);
    else merged.push({ role: m.role, content: blocks });
  }
  while (merged.length && merged[0].role !== "user") merged.shift();
  if (!merged.length) return json({ error: { message: "no messages" } }, 400);
  const up = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": env.ANTHROPIC_API_KEY, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({ model: env.MODEL || "claude-haiku-4-5", max_tokens: body.max_tokens, stream, system, messages: merged }),
  });
  if (!up.ok) return up;
  if (stream) return new Response(anthropicToOpenAIStream(up.body), { headers: { "content-type": "text/event-stream", "cache-control": "no-store" } });
  const data = await up.json();
  const text = (data.content || []).filter(b => b.type === "text").map(b => b.text).join("");
  return json({ choices: [{ message: { role: "assistant", content: text } }] });
}

export default {
  async fetch(request, env) {
    if (request.method === "GET") return new Response("Khotwa AI proxy OK", { status: 200 });
    if (request.method !== "POST") return new Response("method not allowed", { status: 405 });
    if (env.APP_TOKEN && request.headers.get("X-Khotwa-Token") !== env.APP_TOKEN) {
      return json({ error: { message: "unauthorized" } }, 401);
    }
    const raw = await request.text();
    if (raw.length > MAX_BODY) return json({ error: { message: "too large" } }, 413);
    let req;
    try { req = JSON.parse(raw); } catch { return json({ error: { message: "bad json" } }, 400); }

    const stream = req.stream === true;
    const body = {
      messages: Array.isArray(req.messages) ? req.messages : [],
      temperature: typeof req.temperature === "number" ? req.temperature : 0.4,
      max_tokens: Math.min(Number(req.max_tokens) || 600, MAX_TOKENS),
      stream,
    };
    if (!body.messages.length) return json({ error: { message: "no messages" } }, 400);

    const providers = [];
    if (env.GEMINI_API_KEY) providers.push(() => openAICompatible(
      "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
      env.GEMINI_API_KEY, env.MODEL || "gemini-2.5-flash", { ...body, reasoning_effort: "none" }));
    if (env.OPENAI_API_KEY) providers.push(() => openAICompatible(
      "https://api.openai.com/v1/chat/completions", env.OPENAI_API_KEY, env.MODEL || "gpt-4o-mini", body));
    if (env.ANTHROPIC_API_KEY) providers.push(() => viaClaude(env, body, stream));
    if (!providers.length) return json({ error: { message: "proxy has no provider key" } }, 500);

    let last = null;
    for (const call of providers) {
      try {
        const up = await call();
        if (up.ok) {
          return new Response(up.body, {
            headers: { "content-type": stream ? "text/event-stream" : "application/json", "cache-control": "no-store" },
          });
        }
        last = up.status;
      } catch (e) {
        last = String(e);
      }
    }
    // 429 يجعل التطبيق ينتقل فوراً إلى المحرك التالي
    return json({ error: { message: "upstream " + last } }, last === 429 ? 429 : 502);
  },
};
