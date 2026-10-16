import { randomBytes, createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
const token = randomBytes(32).toString("base64url");
writeFileSync(new URL(".client-token", import.meta.url), token + "\n", {
  mode: 0o600,
  flag: "wx",
});
console.log("Client token saved to .client-token (private; not printed).");
console.log(
  "CLIENT_TOKEN_SHA256=" + createHash("sha256").update(token).digest("hex"),
);
