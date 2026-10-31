"use client";
import { m, useMotionTemplate, useMotionValue } from "framer-motion";
import { Bell, Check } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { BorderBeam } from "@/components/ui/border-beam";
import { AppIcon } from "@/components/ui/app-icon";
import { notifyHref, type AppId } from "@/lib/site";
import { cn } from "@/lib/utils";

export type Feature = { icon: LucideIcon; title: string; body: string };
export type Plan = { name: string; price: string; note: string; blurb: string; features: string[]; highlight?: boolean; badge?: string };

/** Centred product heading: icon, eyebrow, title, one line under it. */
export function ProductHeading({
  app,
  eyebrow,
  title,
  sub,
  accent,
}: {
  app: AppId;
  eyebrow: string;
  title: React.ReactNode;
  sub: string;
  accent: string;
}) {
  return (
    <BlurFade className="mx-auto mb-12 max-w-2xl text-center">
      <AppIcon app={app} size={60} className="mx-auto" />
      <p className="mt-5 text-xs font-semibold uppercase tracking-[0.2em]" style={{ color: accent }}>
        {eyebrow}
      </p>
      <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-5xl">{title}</h2>
      <p className="mt-4 text-lg text-ink-muted">{sub}</p>
    </BlurFade>
  );
}

function SpotlightCard({ icon: Icon, title, body, accent }: Feature & { accent: string }) {
  const x = useMotionValue(-200);
  const y = useMotionValue(-200);
  const bg = useMotionTemplate`radial-gradient(260px circle at ${x}px ${y}px, ${accent}2e, transparent 70%)`;
  return (
    <m.article
      onMouseMove={(e) => {
        const r = e.currentTarget.getBoundingClientRect();
        x.set(e.clientX - r.left);
        y.set(e.clientY - r.top);
      }}
      onMouseLeave={() => {
        x.set(-200);
        y.set(-200);
      }}
      whileHover={{ y: -6 }}
      transition={{ type: "spring", stiffness: 300, damping: 24 }}
      className="group relative h-full overflow-hidden rounded-3xl border border-line bg-raised p-5 shadow-card sm:p-6"
    >
      <m.div aria-hidden className="pointer-events-none absolute inset-0" style={{ background: bg }} />
      <div className="relative flex gap-4 text-left sm:block">
        <span
          className="grid size-11 shrink-0 place-items-center rounded-2xl text-white transition-transform duration-300 group-hover:rotate-3 group-hover:scale-110"
          style={{ background: `linear-gradient(135deg,#334155,${accent})` }}
        >
          <Icon size={20} />
        </span>
        <div>
          <h3 className="text-lg font-semibold sm:mt-4">{title}</h3>
          <p className="mt-1 text-[15px] leading-relaxed text-ink-muted sm:mt-1.5">{body}</p>
        </div>
      </div>
    </m.article>
  );
}

export function FeatureGrid({ features, accent, twoCols = false }: { features: Feature[]; accent: string; twoCols?: boolean }) {
  return (
    <div className={cn("grid gap-3 sm:grid-cols-2 sm:gap-4", !twoCols && "lg:grid-cols-3")}>
      {features.map((f, i) => (
        <BlurFade key={f.title} delay={(i % 3) * 0.07} className="h-full">
          <SpotlightCard {...f} accent={accent} />
        </BlurFade>
      ))}
    </div>
  );
}

/** Free vs Pro (or paid) cards. The button asks to be notified until the app is on Google Play. */
export function PlanCards({ plans, accent, accentSoft, appName }: { plans: Plan[]; accent: string; accentSoft: string; appName: string }) {
  return (
    <div className="mx-auto mt-14 grid max-w-4xl gap-5 md:grid-cols-2">
      {plans.map((p, i) => (
        <BlurFade key={p.name} delay={i * 0.1} className="h-full">
          <m.div
            whileHover={{ y: -6 }}
            transition={{ type: "spring", stiffness: 300, damping: 24 }}
            className={cn("relative flex h-full flex-col overflow-hidden rounded-3xl border bg-raised p-7 text-left sm:p-8", p.highlight ? "" : "border-line")}
            style={p.highlight ? { borderColor: `${accent}66`, background: `linear-gradient(180deg, ${accent}22, #1C1820 60%)` } : undefined}
          >
            {p.highlight && <BorderBeam size={120} duration={8} colorFrom={accentSoft} colorTo={accent} />}
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">{p.name}</h3>
              {p.highlight && (
                <span className="rounded-full px-3 py-1 text-xs font-semibold" style={{ background: `${accent}33`, color: accentSoft }}>
                  {p.badge ?? "Pay once"}
                </span>
              )}
            </div>
            <p className="mt-4 text-5xl font-extrabold tracking-tight">
              {p.price} <span className="text-base font-medium text-ink-muted">{p.note}</span>
            </p>
            <p className="mt-3 text-ink-muted">{p.blurb}</p>
            <ul className="mt-6 flex-1 space-y-3">
              {p.features.map((f) => (
                <li key={f} className="flex items-start gap-3 text-sm">
                  <Check size={18} className="mt-0.5 shrink-0" style={{ color: p.highlight ? accentSoft : "#8A8193" }} /> {f}
                </li>
              ))}
            </ul>
            <a
              href={notifyHref(appName)}
              className={cn(
                "mt-8 inline-flex items-center justify-center gap-2 rounded-full px-6 py-3 font-semibold transition-all duration-200 hover:scale-[1.02]",
                p.highlight ? "text-white" : "border border-line text-ink hover:bg-white/5",
              )}
              style={p.highlight ? { background: `linear-gradient(135deg,#334155,${accent})` } : undefined}
            >
              <Bell size={16} /> Notify me at launch
            </a>
          </m.div>
        </BlurFade>
      ))}
    </div>
  );
}
