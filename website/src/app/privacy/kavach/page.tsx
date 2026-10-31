import type { Metadata } from "next";
import { readFileSync } from "node:fs";
import path from "node:path";

export const metadata: Metadata = {
  title: "Ampli Kavach Privacy Policy",
  description: "Privacy policy for the Ampli Kavach app.",
  alternates: { canonical: "/privacy/kavach/" },
};

// Generated (pandoc) from /mnt/project-files/ampli-kavach/privacy.md (owned by the Kavach thread), so the site matches the app.
const html = readFileSync(path.join(process.cwd(), "src/content/privacy-kavach.html"), "utf8");

export default function KavachPrivacy() {
  return (
    <article className="container max-w-3xl py-16">
      <div className="prose-ampli" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
