"use client";
import { ArrowRight, HeartHandshake } from "lucide-react";
import { m, useReducedMotion } from "framer-motion";
import { BlurFade } from "@/components/ui/blur-fade";
import { ShimmerLink } from "@/components/ui/shimmer-button";
import { AppIcon } from "@/components/ui/app-icon";
import { products } from "@/lib/site";

// Tile centres inside the square, in percent.
const spots = [
  { left: "22%", top: "30%", d: 0 },
  { left: "78%", top: "30%", d: 0.6 },
  { left: "22%", top: "72%", d: 1.2 },
  { left: "78%", top: "72%", d: 1.8 },
];

/** Four app tiles floating on a slowly turning ring, drawn in code (no heavy hero image). */
function AppCluster() {
  const reduce = useReducedMotion();
  return (
    <div className="relative mx-auto aspect-square w-full max-w-[420px]" aria-hidden>
      <m.div
        className="absolute inset-[8%] rounded-full border border-dashed border-white/10"
        animate={reduce ? undefined : { rotate: 360 }}
        transition={{ duration: 60, repeat: Infinity, ease: "linear" }}
      />
      <div className="absolute inset-[22%] rounded-full border border-white/5" />
      <div className="absolute inset-[30%] rounded-full bg-[conic-gradient(from_0deg,#E11D48,#0891B2,#0D9488,#EA580C,#E11D48)] opacity-25 blur-2xl" />
      {products.map((p, i) => (
        <m.a
          key={p.id}
          href={`#${p.id}`}
          tabIndex={-1}
          className="absolute flex w-[32%] flex-col items-center gap-2 rounded-3xl border border-white/10 bg-raised/80 p-3 text-center backdrop-blur sm:p-4"
          style={{ left: spots[i].left, top: spots[i].top, x: "-50%", y: "-50%" }}
          initial={{ opacity: 0, scale: 0.8 }}
          animate={reduce ? { opacity: 1, scale: 1 } : { opacity: 1, scale: 1, y: ["-50%", "-56%", "-50%"] }}
          transition={{
            opacity: { delay: 0.2 + i * 0.12, duration: 0.5 },
            scale: { delay: 0.2 + i * 0.12, duration: 0.5 },
            y: { delay: spots[i].d, duration: 5, repeat: Infinity, ease: "easeInOut" },
          }}
        >
          <AppIcon app={p.icon} size={56} className="size-12 sm:size-14" />
          <span className="text-sm font-semibold">{p.short}</span>
          <span className="h-1 w-10 rounded-full" style={{ background: p.accent }} />
        </m.a>
      ))}
    </div>
  );
}

export function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-[-20%] -z-10 h-[640px] bg-[radial-gradient(50%_45%_at_30%_30%,rgba(225,29,72,.22),transparent_70%),radial-gradient(40%_40%_at_75%_35%,rgba(8,145,178,.18),transparent_70%),radial-gradient(35%_35%_at_55%_70%,rgba(234,88,12,.14),transparent_70%)]"
      />
      <div className="container grid items-center gap-12 pb-20 pt-10 text-center md:grid-cols-[1.1fr_.9fr] md:pb-28 md:pt-16 md:text-left">
        <div>
          <BlurFade inView={false} visibleFirst>
            <span className="inline-flex items-center gap-2 rounded-full border border-line bg-raised/60 px-3 py-1 text-xs text-ink-muted">
              <span className="size-1.5 rounded-full bg-rose-400" /> Made in India · 4 apps, 0 monthly fees
            </span>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.08}>
            <h1 className="mt-5 text-[2.5rem] font-extrabold leading-[1.05] tracking-tight sm:text-6xl">
              Desi apps,
              <br />
              <span className="bg-[linear-gradient(90deg,#FDA4AF,#67E8F9_55%,#FDBA74)] bg-clip-text text-transparent">built with heart.</span>
            </h1>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.16}>
            <p className="mx-auto mt-5 max-w-xl text-lg text-ink-muted sm:text-xl md:mx-0">
              A music player with no monthly fee, a car system that talks in Haryanvi, a scam-call shield for your family, and free sarkari job alerts. Four
              Android apps from one small Indian studio.
            </p>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.24}>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3 md:justify-start">
              <ShimmerLink href="#apps">
                See our apps <ArrowRight size={18} />
              </ShimmerLink>
              <a
                href="#support"
                className="group inline-flex items-center gap-2 rounded-full border border-line px-6 py-3 font-semibold text-ink transition-colors hover:border-rose-400/50 hover:bg-white/5"
              >
                <HeartHandshake size={18} className="text-rose-300" /> Support Ampli
              </a>
            </div>
            <p className="mt-4 text-sm text-ink-faint">Coming soon to Google Play · No monthly fees, ever</p>
          </BlurFade>
        </div>
        <BlurFade inView={false} visibleFirst delay={0.2} offset={24}>
          <AppCluster />
        </BlurFade>
      </div>
    </section>
  );
}
