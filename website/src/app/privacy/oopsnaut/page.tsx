import type { Metadata } from "next";
import { readFileSync } from "node:fs";
import path from "node:path";

export const metadata: Metadata = {
  title: "Oopsnaut Privacy Policy",
  description: "Privacy policy for the Oopsnaut game.",
  alternates: { canonical: "/privacy/oopsnaut/" },
};

// Oopsnaut privacy policy (com.ampli.oopsnaut), covering AdMob, UMP and Firebase Analytics.
const html = readFileSync(path.join(process.cwd(), "src/content/privacy-oopsnaut.html"), "utf8");

export default function OopsnautPrivacy() {
  return (
    <article className="container max-w-3xl py-16">
      <div className="prose-ampli" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
