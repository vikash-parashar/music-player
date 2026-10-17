"use client";
import { m } from "framer-motion";
import { Download, Star, MessageSquareHeart, Share2, HandCoins, Mail } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { BorderBeam } from "@/components/ui/border-beam";
import { feedbackHref, shareText, site } from "@/lib/site";

const steps: { icon: LucideIcon; title: string; body: string; cta?: { label: string; href: string } }[] = [
  { icon: Download, title: "Download", body: "Install our apps from Google Play when they launch. Every download tells Google that people want desi apps." },
  { icon: Star, title: "Rate honestly", body: "Use the app for a few days, then rate it and write what you really think. Honest reviews help other people find us." },
  {
    icon: MessageSquareHeart,
    title: "Send feedback",
    body: "Found a bug or want a feature? Tell us. We read every message and build what you ask for.",
    cta: { label: "Email us", href: feedbackHref },
  },
  {
    icon: Share2,
    title: "Be our marketing team",
    body: "We have no big ad budget. Share Ampli on WhatsApp, Instagram and in your family groups. That is our marketing.",
    cta: { label: "Share on WhatsApp", href: `https://wa.me/?text=${encodeURIComponent(shareText)}` },
  },
  {
    icon: HandCoins,
    title: "Support with money",
    body: "If you can, a small amount helps pay for servers, voices and Play fees. A UPI link is coming here soon.",
  },
];

export function Support() {
  return (
    <section id="support" className="relative scroll-mt-24 overflow-hidden border-t border-line py-24">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(45%_50%_at_20%_20%,rgba(225,29,72,.16),transparent_70%),radial-gradient(45%_50%_at_80%_30%,rgba(8,145,178,.14),transparent_70%)]"
      />
      <div className="container">
        <BlurFade className="mx-auto max-w-3xl text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-rose-300">Saath do</p>
          <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-5xl" lang="hi">
            आपका साथ, हरियाणा का नाम।
          </h2>
          <p className="mt-3 text-xl font-semibold text-ink">Help us take Haryana, and India, to the top.</p>
          <p className="mx-auto mt-5 max-w-2xl text-lg leading-relaxed text-ink-muted">
            Ampli is one developer with a laptop, a dream and very little money. No investors, no big team, just apps built for people like us. If you believe desi apps can be world class, here is how you can help, and most of it is free.
          </p>
        </BlurFade>

        <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {steps.map((s, i) => (
            <BlurFade key={s.title} delay={i * 0.07} className="h-full">
              <m.div
                whileHover={{ y: -6 }}
                transition={{ type: "spring", stiffness: 300, damping: 24 }}
                className="relative flex h-full flex-col overflow-hidden rounded-3xl border border-line bg-raised p-6 text-center"
              >
                {i === 4 && <BorderBeam size={90} duration={7} colorFrom="#FDBA74" colorTo="#E11D48" />}
                <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-brand text-white">
                  <s.icon size={22} />
                </span>
                <p className="mt-1 text-xs font-semibold text-ink-faint">Step {i + 1}</p>
                <h3 className="mt-2 text-lg font-semibold">{s.title}</h3>
                <p className="mt-2 flex-1 text-sm leading-relaxed text-ink-muted">{s.body}</p>
                {s.cta && (
                  <a
                    href={s.cta.href}
                    target={s.cta.href.startsWith("http") ? "_blank" : undefined}
                    rel="noopener noreferrer"
                    className="mt-5 inline-flex justify-center rounded-full border border-line px-4 py-2 text-sm font-semibold transition-colors hover:border-rose-400/50 hover:bg-white/5"
                  >
                    {s.cta.label}
                  </a>
                )}
                {i === 4 && (
                  <span className="mt-5 inline-flex justify-center rounded-full bg-white/5 px-4 py-2 text-sm font-semibold text-ink-muted">UPI coming soon</span>
                )}
              </m.div>
            </BlurFade>
          ))}
        </div>

        <BlurFade className="mt-10 flex flex-wrap items-center justify-center gap-2 text-sm text-ink-faint">
          <Mail size={16} /> Questions or ideas? Write to{" "}
          <a href={`mailto:${site.email}`} className="text-rose-300 underline underline-offset-2">
            {site.email}
          </a>
        </BlurFade>
      </div>
    </section>
  );
}
