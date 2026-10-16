import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import sharp from "sharp";
import { validateAnalysis, makeGeminiAnalyzer } from "./analysis.js";
import { createApp } from "./server.js";
const sample = {
  name: "Nasi ayam",
  components: [
    {
      name: "Chicken and rice",
      ingredients: ["rice", "chicken"],
      serving: "1 plate, confirm portion",
      gramsMin: 250,
      gramsMax: 400,
      caloriesMin: 450,
      caloriesMax: 750,
      protein: 30,
      carbs: 65,
      fat: 20,
      fibre: 3,
    },
  ],
  cookingMethod: "Steamed rice, roasted chicken",
  sauces: ["chilli sauce"],
  confidence: "medium",
  uncertainty: "Hidden oil and serving size vary. Confirm your portion.",
  source: "image_estimate",
};
test("accepts structured component estimates", () =>
  assert.equal(validateAnalysis(sample).components.length, 1));
test("rejects inverted ranges, negative/non-finite nutrition, missing uncertainty and empty foods", () => {
  for (const bad of [
    { ...sample, components: [] },
    { ...sample, uncertainty: "" },
    { ...sample, components: [{ ...sample.components[0], gramsMin: 500 }] },
    { ...sample, components: [{ ...sample.components[0], fat: -1 }] },
    { ...sample, components: [{ ...sample.components[0], protein: NaN }] },
  ])
    assert.throws(() => validateAnalysis(bad));
});
test("Gemini request uses official endpoint, structured JSON and image", async () => {
  const analyze = makeGeminiAnalyzer({
    apiKey: "test-key",
    fetchImpl: async (url, init) => {
      assert.match(
        url,
        /^https:\/\/generativelanguage.googleapis.com\/v1beta\/models\//,
      );
      const b = JSON.parse(init.body);
      assert.equal(b.generationConfig.responseMimeType, "application/json");
      assert.equal(b.contents[0].parts[1].inlineData.mimeType, "image/jpeg");
      return {
        ok: true,
        json: async () => ({
          candidates: [
            {
              finishReason: "STOP",
              content: { parts: [{ text: JSON.stringify(sample) }] },
            },
          ],
        }),
      };
    },
  });
  assert.deepEqual(await analyze(Buffer.from("test"), "food"), sample);
});
test("malformed, blocked and truncated AI output rejected", async () => {
  for (const candidate of [
    { finishReason: "STOP", content: { parts: [{ text: "not json" }] } },
    {
      finishReason: "MAX_TOKENS",
      content: { parts: [{ text: JSON.stringify(sample) }] },
    },
    {},
  ]) {
    const analyze = makeGeminiAnalyzer({
      apiKey: "test",
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({ candidates: [candidate] }),
      }),
    });
    await assert.rejects(
      analyze(Buffer.from("x"), "food"),
      (e) => e.status === 422,
    );
  }
});
test("upstream quotas and network errors handled safely", async () => {
  await assert.rejects(
    makeGeminiAnalyzer({
      apiKey: "test",
      fetchImpl: async () => ({ ok: false, status: 429 }),
    })(Buffer.from("x"), "food"),
    (e) => e.status === 429,
  );
  await assert.rejects(
    makeGeminiAnalyzer({
      apiKey: "test",
      fetchImpl: async () => {
        throw Error("private upstream details");
      },
    })(Buffer.from("x"), "food"),
    (e) => e.status === 504 && !e.message.includes("private"),
  );
});
test("HTTP proxy auth, actual image decoding, request validation and rate limiting", async () => {
  let calls = 0;
  const token = "a".repeat(40),
    app = createApp({
      tokenHash: createHash("sha256").update(token).digest("hex"),
      analyze: async () => {
        calls++;
        return sample;
      },
      rateMax: 3,
    });
  const server = app.listen(0, "127.0.0.1");
  await new Promise((r) => server.once("listening", r));
  const url = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(url + "/health")).status, 200);
    assert.equal(
      (await fetch(url + "/analyze", { method: "POST" })).status,
      401,
    );
    const bytes = await sharp({
      create: { width: 10, height: 10, channels: 3, background: "#ffffff" },
    })
      .jpeg()
      .toBuffer();
    const post = async (image, mode = "food") => {
      const f = new FormData();
      f.append("image", new Blob([image], { type: "image/jpeg" }), "food.jpg");
      f.append("mode", mode);
      return fetch(url + "/analyze", {
        method: "POST",
        headers: { authorization: `Bearer ${token}` },
        body: f,
      });
    };
    assert.equal((await post(bytes, "bad")).status, 400);
    assert.equal((await post(Buffer.from("not an image"))).status, 400);
    const good = await post(bytes);
    assert.equal(good.status, 200);
    assert.deepEqual(await good.json(), sample);
    assert.equal(calls, 1);
    assert.equal((await post(bytes)).status, 429);
  } finally {
    await new Promise((r) => server.close(r));
  }
});
