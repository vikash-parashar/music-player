import type { Metadata } from "next";
import { readFileSync } from "node:fs";
import path from "node:path";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "Privacy policy for the Ampli MP3 Player Android app.",
  alternates: { canonical: "/privacy/" },
};

// Generated from the repo's PRIVACY.md (pandoc) so the site and the app never disagree.
const html = readFileSync(path.join(process.cwd(), "src/content/privacy.html"), "utf8");

export default function Privacy() {
  return (
    <article className="container max-w-3xl py-16">
      <div className="prose-ampli" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
