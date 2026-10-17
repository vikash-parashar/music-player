import type { Metadata } from "next";
import { readFileSync } from "node:fs";
import path from "node:path";

export const metadata: Metadata = {
  title: "Ampli Car Hub Privacy Policy",
  description: "Privacy policy for the Ampli Car Hub Android app.",
  alternates: { canonical: "/privacy/carhub/" },
};

// Generated (pandoc) from docs/privacy-policy.md in the ampli-drive repo, so the site matches the app.
const html = readFileSync(path.join(process.cwd(), "src/content/privacy-carhub.html"), "utf8");

export default function CarHubPrivacy() {
  return (
    <article className="container max-w-3xl py-16">
      <div className="prose-ampli" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
