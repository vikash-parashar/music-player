"use client";
import { m } from "framer-motion";
import { Check } from "lucide-react";
import { BorderBeam } from "@/components/ui/border-beam";
import { BlurFade } from "@/components/ui/blur-fade";
import { SectionHeading } from "@/components/sections/section-heading";
import { site } from "@/lib/site";
import { cn } from "@/lib/utils";

const plans = [
  {
    name: "Free",
    price: "₹0",
    note: "forever",
    blurb: "Every music feature, no limits on your own songs.",
    features: ["All offline playback features", "Equalizer, lyrics, playlists", "Ringtone maker & car mode", "Audiobooks & podcasts", "Small banner ad"],
    cta: "Download free",
    highlight: false,
  },
  {
    name: "Ampli Pro",
    price: "₹199",
    note: "one time · lifetime",
    blurb: "Support an Indian indie app and unlock the extras.",
    features: ["Everything in Free", "No ads, ever", "Video player", "Extra themes & progress bars", "Chromecast to your TV"],
    cta: "Get Ampli Pro",
    highlight: true,
  },
];

export function Pricing() {
  return (
    <section id="pricing" className="scroll-mt-24 py-24">
      <div className="container">
        <SectionHeading eyebrow="Pricing" title="Pay once. Play forever." sub="No subscription. No hidden charges. Buy Pro inside the app with Google Play." />
        <div className="mx-auto grid max-w-4xl gap-5 md:grid-cols-2">
          {plans.map((p, i) => (
            <BlurFade key={p.name} delay={i * 0.1} className="h-full">
              <m.div
                whileHover={{ y: -6 }}
                transition={{ type: "spring", stiffness: 300, damping: 24 }}
                className={cn(
                  "relative flex h-full flex-col overflow-hidden rounded-3xl border p-8",
                  p.highlight ? "border-rose-400/30 bg-gradient-to-b from-[#2a1720] to-raised shadow-glow" : "border-line bg-raised",
                )}
              >
                {p.highlight && <BorderBeam size={120} duration={8} />}
                <div className="flex items-center justify-between">
                  <h3 className="text-lg font-semibold">{p.name}</h3>
                  {p.highlight && <span className="rounded-full bg-rose-600/20 px-3 py-1 text-xs font-semibold text-rose-300">Best value</span>}
                </div>
                <p className="mt-4 text-5xl font-extrabold tracking-tight">
                  {p.price} <span className="text-base font-medium text-ink-muted">{p.note}</span>
                </p>
                <p className="mt-3 text-ink-muted">{p.blurb}</p>
                <ul className="mt-6 space-y-3">
                  {p.features.map((f) => (
                    <li key={f} className="flex items-start gap-3 text-sm">
                      <Check size={18} className={cn("mt-0.5 shrink-0", p.highlight ? "text-rose-300" : "text-ink-faint")} /> {f}
                    </li>
                  ))}
                </ul>
                <a
                  href={site.playUrl}
                  className={cn(
                    "mt-8 inline-flex justify-center rounded-full px-6 py-3 font-semibold transition-all duration-200",
                    p.highlight ? "bg-brand text-white hover:scale-[1.02] hover:shadow-glow" : "border border-line text-ink hover:bg-white/5",
                  )}
                >
                  {p.cta}
                </a>
              </m.div>
            </BlurFade>
          ))}
        </div>
      </div>
    </section>
  );
}
