import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import multer from "multer";
import sharp from "sharp";
import { createHash, timingSafeEqual } from "node:crypto";
import { pathToFileURL } from "node:url";
import {
  makeGeminiAnalyzer,
  validateAnalysis,
  AnalysisError,
} from "./analysis.js";
export function createApp({
  tokenHash,
  analyze,
  rateMax = 10,
  trustProxy = false,
}) {
  if (!/^[a-f0-9]{64}$/.test(tokenHash ?? ""))
    throw Error("CLIENT_TOKEN_SHA256 must be a SHA-256 hex digest");
  const app = express();
  app.disable("x-powered-by");
  app.set("trust proxy", trustProxy);
  app.use(helmet());
  app.use((_req, res, next) => {
    res.set("Cache-Control", "no-store");
    next();
  });
  app.get("/health", (_req, res) => res.json({ status: "ok" }));
  const ipLimit = rateLimit({
    windowMs: 60000,
    limit: 30,
    standardHeaders: "draft-8",
    legacyHeaders: false,
  });
  const tokenLimit = rateLimit({
    windowMs: 60000,
    limit: rateMax,
    keyGenerator: () => "personal-client",
    standardHeaders: "draft-8",
    legacyHeaders: false,
  });
  let inFlight = 0;
  const concurrency = (req, res, next) => {
    if (inFlight >= 2)
      return res.status(503).json({ error: "Scanner busy. Try shortly." });
    inFlight++;
    let released = false;
    const release = () => {
      if (!released) {
        released = true;
        inFlight--;
      }
    };
    res.once("finish", release);
    res.once("close", release);
    next();
  };
  const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 5 * 1024 * 1024, files: 1, fields: 1, parts: 3 },
    fileFilter: (_req, file, cb) =>
      cb(
        null,
        ["image/jpeg", "image/png", "image/webp"].includes(file.mimetype),
      ),
  });
  app.post(
    "/analyze",
    ipLimit,
    (req, res, next) => {
      const header = req.get("authorization") ?? "";
      if (header.length > 300 || !header.startsWith("Bearer "))
        return res.status(401).json({ error: "Unauthorized" });
      if (
        !timingSafeEqual(
          createHash("sha256").update(header.slice(7)).digest(),
          Buffer.from(tokenHash, "hex"),
        )
      )
        return res.status(401).json({ error: "Unauthorized" });
      next();
    },
    tokenLimit,
    concurrency,
    upload.single("image"),
    async (req, res) => {
      if (!req.file || !["food", "label"].includes(req.body?.mode))
        return res
          .status(400)
          .json({
            error: "JPEG, PNG or WebP image and food/label mode required",
          });
      try {
        let image;
        try {
          const decoded = sharp(req.file.buffer, {
            limitInputPixels: 20000000,
            animated: false,
          });
          const metadata = await decoded.metadata();
          if (!["jpeg", "png", "webp"].includes(metadata.format))
            throw Error("Unsupported image");
          image = await decoded
            .rotate()
            .resize({
              width: 1600,
              height: 1600,
              fit: "inside",
              withoutEnlargement: true,
            })
            .jpeg({ quality: 85 })
            .toBuffer();
        } catch {
          throw new AnalysisError(400, "Invalid or oversized image");
        }
        // No images, request bodies, credentials or responses written to logs or disk.
        res.json(validateAnalysis(await analyze(image, req.body.mode)));
      } catch (e) {
        res
          .status(e instanceof AnalysisError ? e.status : 422)
          .json({
            error:
              e instanceof AnalysisError
                ? e.message
                : "Invalid AI response. Try another image or log manually.",
          });
      }
    },
  );
  app.use((error, _req, res, _next) =>
    res
      .status(error instanceof multer.MulterError ? 413 : 400)
      .json({ error: "Invalid upload or request too large" }),
  );
  return app;
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const app = createApp({
    tokenHash: process.env.CLIENT_TOKEN_SHA256,
    analyze: makeGeminiAnalyzer({
      apiKey: process.env.GEMINI_API_KEY,
      model: process.env.GEMINI_MODEL ?? "gemini-2.5-flash",
    }),
    trustProxy: process.env.TRUST_PROXY === "1" ? 1 : false,
  });
  const server = app.listen(Number(process.env.PORT ?? 8080), "0.0.0.0", () =>
    console.log("LEANOS scanner listening"),
  );
  server.requestTimeout = 70000;
  server.headersTimeout = 10000;
  server.maxRequestsPerSocket = 100;
  for (const signal of ["SIGTERM", "SIGINT"])
    process.on(signal, () => server.close(() => process.exit(0)));
}
