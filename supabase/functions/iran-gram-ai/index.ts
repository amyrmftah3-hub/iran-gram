import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });
  }

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) {
    return Response.json(
      { code: "AI_NOT_CONFIGURED", error: "GEMINI_API_KEY is not configured" },
      { status: 503, headers: corsHeaders },
    );
  }

  try {
    const body = await req.json();
    const incoming = Array.isArray(body?.messages) ? body.messages.slice(-16) : [];
    const contents = incoming
      .filter((m: any) => (m?.role === "user" || m?.role === "assistant") && typeof m?.text === "string")
      .map((m: any) => ({
        role: m.role === "assistant" ? "model" : "user",
        parts: [{ text: String(m.text).slice(0, 5000) }],
      }));

    if (!contents.length || contents[contents.length - 1].role !== "user") {
      return Response.json({ error: "A user message is required" }, { status: 400, headers: corsHeaders });
    }

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey,
        },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{
              text: "تو آرو، دستیار هوشمند فارسی پیام‌رسان ایران‌گرام هستی. دوستانه، دقیق و محترمانه پاسخ بده. پیش‌فرض فارسی بنویس. اگر مطمئن نیستی، صادقانه بگو. در موضوعات آموزشی مرحله‌به‌مرحله توضیح بده."
            }],
          },
          contents,
          generationConfig: {
            temperature: 0.7,
            maxOutputTokens: 1200,
          },
        }),
      },
    );

    const result = await response.json();
    if (!response.ok) {
      console.error("Gemini API error", response.status, result?.error?.message);
      return Response.json(
        { error: "AI_PROVIDER_ERROR", detail: result?.error?.message || "Provider request failed" },
        { status: 502, headers: corsHeaders },
      );
    }

    const reply = result?.candidates?.[0]?.content?.parts
      ?.map((part: any) => typeof part.text === "string" ? part.text : "")
      .join("")
      .trim();

    if (!reply) {
      return Response.json({ error: "AI_EMPTY_RESPONSE" }, { status: 502, headers: corsHeaders });
    }

    return Response.json({ reply }, { headers: corsHeaders });
  } catch (error) {
    console.error("IranGram AI error", error);
    return Response.json({ error: "Invalid request" }, { status: 400, headers: corsHeaders });
  }
});
