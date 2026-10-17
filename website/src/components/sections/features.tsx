"use client";
import { m, useMotionTemplate, useMotionValue } from "framer-motion";
import { SlidersHorizontal, Car, Scissors, Music2, Languages, Wifi, Moon, Mic2, BookOpen } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { SectionHeading } from "@/components/sections/section-heading";

const main: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: SlidersHorizontal,
    title: "Equalizer for every device",
    body: "Ampli remembers a different sound for your earphones, Bluetooth speaker and car, and switches by itself when you connect.",
  },
  {
    icon: Car,
    title: "Car mode that keeps eyes on the road",
    body: "Huge buttons, swipe to skip and voice search in Hindi or English, so you touch the screen less while driving.",
  },
  {
    icon: Scissors,
    title: "Ringtone maker",
    body: "Cut any song into a ringtone, alarm or a special tone for one contact, with smooth fades. Free, forever.",
  },
];

const extras: { icon: LucideIcon; label: string }[] = [
  { icon: Music2, label: "Songs, albums, artists, folders" },
  { icon: Mic2, label: "Lyrics from your files" },
  { icon: Wifi, label: "Wi‑Fi transfer from laptop" },
  { icon: Moon, label: "Sleep timer & speed control" },
  { icon: BookOpen, label: "Free audiobooks & podcasts" },
  { icon: Languages, label: "6 Indian languages" },
];

function SpotlightCard({ icon: Icon, title, body }: (typeof main)[number]) {
  const x = useMotionValue(-200);
  const y = useMotionValue(-200);
  const bg = useMotionTemplate`radial-gradient(260px circle at ${x}px ${y}px, rgba(225,29,72,.18), transparent 70%)`;
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
      className="group relative h-full overflow-hidden rounded-3xl border border-line bg-raised p-7 shadow-card"
    >
      <m.div aria-hidden className="pointer-events-none absolute inset-0" style={{ background: bg }} />
      <div className="relative">
        <span className="grid size-12 place-items-center rounded-2xl bg-brand text-white shadow-glow transition-transform duration-300 group-hover:scale-110 group-hover:rotate-3">
          <Icon size={22} />
        </span>
        <h3 className="mt-5 text-xl font-semibold">{title}</h3>
        <p className="mt-2 leading-relaxed text-ink-muted">{body}</p>
      </div>
    </m.article>
  );
}

export function Features() {
  return (
    <section id="features" className="scroll-mt-24 py-24">
      <div className="container">
        <SectionHeading eyebrow="Features" title="Everything a music lover needs" sub="Free, with no limits on your own songs." />
        <div className="grid gap-5 md:grid-cols-3">
          {main.map((f, i) => (
            <BlurFade key={f.title} delay={i * 0.08} className="h-full">
              <SpotlightCard {...f} />
            </BlurFade>
          ))}
        </div>
        <BlurFade delay={0.1}>
          <ul className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-3">
            {extras.map(({ icon: Icon, label }) => (
              <li
                key={label}
                className="flex items-center gap-3 rounded-2xl border border-line bg-raised/60 px-4 py-3.5 text-sm text-ink-muted transition-colors hover:border-rose-400/40 hover:text-ink"
              >
                <Icon size={18} className="shrink-0 text-rose-300" /> {label}
              </li>
            ))}
          </ul>
        </BlurFade>
      </div>
    </section>
  );
}
