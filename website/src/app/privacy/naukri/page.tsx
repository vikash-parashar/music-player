import type { Metadata } from "next";
import { readFileSync } from "node:fs";
import path from "node:path";

export const metadata: Metadata = {
  title: "Ampli Naukri Privacy Policy",
  description: "Privacy policy for the Ampli Naukri app and website.",
  alternates: { canonical: "/privacy/naukri/" },
};

// Generated (pandoc) from /mnt/project-files/ampli-naukri-notes/privacy.md (owned by the Naukri thread), so the site matches the app.
const html = readFileSync(path.join(process.cwd(), "src/content/privacy-naukri.html"), "utf8");

export default function NaukriPrivacy() {
  return (
    <article className="container max-w-3xl py-16">
      <div className="prose-ampli" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
