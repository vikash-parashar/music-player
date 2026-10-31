"use client";
import { m } from "framer-motion";
import { ArrowRight, Check } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { BorderBeam } from "@/components/ui/border-beam";
import { AppIcon } from "@/components/ui/app-icon";
import { SectionHeading } from "@/components/sections/section-heading";
import { products, type Product } from "@/lib/site";

const tone: Record<Product["statusTone"], string> = {
  live: "border-green-900 bg-green-950/60 text-green-300",
  test: "border-amber-900 bg-amber-950/50 text-amber-300",
  dev: "border-sky-900 bg-sky-950/50 text-sky-300",
};

export function AppsOverview() {
  return (
    <section id="apps" className="scroll-mt-24 py-24">
      <div className="container">
        <SectionHeading eyebrow="Our apps" title="Four apps. Zero monthly fees." sub="Each one solves a real, everyday problem, in your language. Free to start, and every upgrade is one-time." />
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {products.map((p, i) => (
            <BlurFade key={p.id} delay={i * 0.1} className="h-full">
              <m.a
                href={`#${p.id}`}
                whileHover={{ y: -8 }}
                transition={{ type: "spring", stiffness: 300, damping: 22 }}
                className="group relative flex h-full flex-col overflow-hidden rounded-3xl border border-line bg-raised p-7 text-center"
              >
                <BorderBeam size={110} duration={9} delay={i * 3} colorFrom={p.accentSoft} colorTo={p.accent} />
                <div
                  aria-hidden
                  className="pointer-events-none absolute -top-24 left-1/2 size-56 -translate-x-1/2 rounded-full opacity-30 blur-3xl transition-opacity duration-500 group-hover:opacity-60"
                  style={{ background: p.accent }}
                />
                <AppIcon app={p.icon} size={76} className="relative mx-auto transition-transform duration-500 group-hover:-rotate-6 group-hover:scale-110" />
                <h3 className="relative mt-5 text-xl font-bold">{p.name}</h3>
                <p className="relative mt-2 text-ink-muted">{p.tagline}</p>
                <span className={`relative mx-auto mt-4 inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-semibold ${tone[p.statusTone]}`}>
                  <span className="size-1.5 rounded-full bg-current" /> {p.status}
                </span>
                <ul className="relative mx-auto mt-5 space-y-2 text-left text-sm">
                  {p.points.map((pt) => (
                    <li key={pt} className="flex items-start gap-2.5">
                      <Check size={16} className="mt-0.5 shrink-0" style={{ color: p.accentSoft }} /> {pt}
                    </li>
                  ))}
                </ul>
                <div className="relative mt-auto pt-6">
                  <span className="inline-block rounded-full border border-line px-3 py-1 text-xs text-ink-muted">{p.price}</span>
                  <span className="mt-4 flex items-center justify-center gap-1.5 text-sm font-semibold" style={{ color: p.accentSoft }}>
                    Explore {p.short} <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
                  </span>
                </div>
              </m.a>
            </BlurFade>
          ))}
        </div>
      </div>
    </section>
  );
}
