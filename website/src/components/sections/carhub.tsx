"use client";
import { useEffect, useState } from "react";
import { AnimatePresence, m, useReducedMotion } from "framer-motion";
import {
  AudioLines,
  Mic,
  Navigation,
  Siren,
  Camera,
  CreditCard,
  Palette,
  Languages,
  Gauge,
  CornerUpRight,
  Fuel,
  CarFront,
} from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { FeatureGrid, PlanCards, ProductHeading, type Feature, type Plan } from "@/components/sections/product-blocks";

const ACCENT = "#0891B2";
const SOFT = "#67E8F9";

/** Real lines from the app (Settings › Voice › Hear samples), family-safe Haryanvi. */
const lines = [
  { voice: "Bunty", hi: "राम राम भाई! गाड्डी त्यार सै, बोल कित चालां?", en: "Ram Ram! The car is ready, say where we go." },
  { voice: "Simran", hi: "सवेरे की राम राम! चाय पी ली के?", en: "Morning Ram Ram! Had your tea?" },
  { voice: "Bunty", hi: "घाम घणा सै, AC चला ले अर पाणी पी ले।", en: "It's very hot, turn on the AC and drink some water." },
  { voice: "Simran", hi: "रात का सफर सै, मैं जाग्गूं सूं, तू भी जाग्या रहिए।", en: "Night trip: I'm awake, you stay awake too." },
  { voice: "Bunty", hi: "आज का सफर बढ़िया रया, राम राम!", en: "Today's trip went well, Ram Ram!" },
];

function HeadUnit() {
  const reduce = useReducedMotion();
  const [i, setI] = useState(0);
  useEffect(() => {
    if (reduce) return;
    const t = setInterval(() => setI((v) => (v + 1) % lines.length), 3800);
    return () => clearInterval(t);
  }, [reduce]);
  const line = lines[i];
  return (
    <div className="relative mx-auto w-full max-w-3xl">
      <div className="absolute -inset-8 -z-10 rounded-[40px] bg-cyan-600/20 blur-3xl" aria-hidden />
      {/* Dashboard bezel */}
      <div className="rounded-[28px] border border-white/10 bg-[#0d1114] p-3 shadow-[0_30px_80px_-20px_rgba(8,145,178,.45)]">
        <div className="grid aspect-[1024/600] grid-cols-[1fr_1.35fr_1fr] gap-2.5 overflow-hidden rounded-[20px] bg-gradient-to-br from-[#0B2733] via-[#0a1a22] to-[#071117] p-3 text-left sm:gap-3 sm:p-4">
          {/* Clock and speed */}
          <div className="flex flex-col justify-between rounded-2xl bg-white/[.04] p-3 sm:p-4">
            <div>
              <p className="text-2xl font-bold tabular-nums sm:text-4xl">9:41</p>
              <p className="text-[10px] text-ink-faint sm:text-xs">Sunday · 31°C Rohtak</p>
            </div>
            <div>
              <p className="flex items-end gap-1 text-3xl font-extrabold tabular-nums text-cyan-300 sm:text-5xl">
                48<span className="mb-1 text-[10px] font-medium text-ink-faint sm:text-xs">km/h</span>
              </p>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-white/10">
                <m.div
                  className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-cyan-300"
                  animate={reduce ? undefined : { width: ["42%", "58%", "48%"] }}
                  transition={{ duration: 6, repeat: Infinity, ease: "easeInOut" }}
                  style={{ width: "48%" }}
                />
              </div>
            </div>
          </div>
          {/* Navigation */}
          <div className="relative overflow-hidden rounded-2xl bg-[#0c2129]">
            <svg viewBox="0 0 200 160" className="absolute inset-0 size-full" aria-hidden preserveAspectRatio="xMidYMid slice">
              <path d="M0 120 C60 110 70 60 120 58 S190 30 200 20" stroke="#164e63" strokeWidth="14" fill="none" />
              <path d="M30 0 L60 160 M140 160 L170 0" stroke="#123c49" strokeWidth="6" fill="none" />
              <m.path
                d="M0 120 C60 110 70 60 120 58 S190 30 200 20"
                stroke="#22D3EE"
                strokeWidth="4"
                fill="none"
                strokeLinecap="round"
                initial={{ pathLength: 0 }}
                animate={{ pathLength: 1 }}
                transition={{ duration: reduce ? 0 : 3, repeat: reduce ? 0 : Infinity, repeatDelay: 1, ease: "easeInOut" }}
              />
              <circle cx="120" cy="58" r="5" fill="#fff" />
            </svg>
            <div className="absolute inset-x-2 top-2 flex items-center gap-2 rounded-xl bg-cyan-600 px-2.5 py-1.5 sm:inset-x-3 sm:top-3 sm:py-2">
              <CornerUpRight size={18} className="shrink-0" />
              <div className="min-w-0">
                <p className="text-xs font-bold sm:text-base">200 मीटर</p>
                <p className="truncate text-[10px] sm:text-xs">आगे सुल्टे हाथ मुड़ जाइए</p>
              </div>
            </div>
          </div>
          {/* Voice */}
          <div className="flex flex-col rounded-2xl bg-white/[.04] p-3 sm:p-4">
            <div className="flex items-center gap-2">
              <span className="grid size-7 place-items-center rounded-full bg-cyan-500/20 text-cyan-300 sm:size-8">
                <Mic size={14} />
              </span>
              <AnimatePresence mode="wait">
                <m.span
                  key={line.voice + i}
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                  className="text-[10px] font-semibold uppercase tracking-wider text-cyan-200 sm:text-xs"
                >
                  {line.voice}
                </m.span>
              </AnimatePresence>
            </div>
            <div className="mt-2 flex h-5 items-end gap-0.5" aria-hidden>
              {Array.from({ length: 14 }).map((_, k) => (
                <span
                  key={k}
                  className="w-1 origin-bottom animate-eq-bar rounded-full bg-cyan-400 motion-reduce:animate-none"
                  style={{ height: `${35 + ((k * 41) % 65)}%`, animationDelay: `${(k % 5) * 0.11}s` }}
                />
              ))}
            </div>
            <div className="relative mt-auto hidden min-h-[5rem] sm:block">
              <AnimatePresence mode="wait">
                <m.p
                  key={i}
                  initial={{ opacity: 0, y: 8, filter: "blur(4px)" }}
                  animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                  exit={{ opacity: 0, y: -8, filter: "blur(4px)" }}
                  transition={{ duration: 0.35 }}
                  className="text-[11px] font-semibold leading-snug sm:text-base"
                  lang="hi"
                >
                  “{line.hi}”
                </m.p>
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
      <AnimatePresence mode="wait">
        <m.div key={i} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} className="mt-4 text-center">
          <p className="mx-auto max-w-sm rounded-2xl border border-cyan-400/20 bg-cyan-500/10 px-4 py-3 font-semibold text-cyan-50 sm:hidden" lang="hi">
            {line.voice}: “{line.hi}”
          </p>
          <p className="mt-2 text-sm text-ink-muted">{line.en}</p>
        </m.div>
      </AnimatePresence>
    </div>
  );
}

const features: Feature[] = [
  { icon: AudioLines, title: "Human Haryanvi voices", body: "Pick Bunty or Simran. 298 real Haryanvi lines, from “Ram Ram” at start to “lock the car” at the end. Works with no internet." },
  { icon: Mic, title: "“Hey Ampli” voice control", body: "Say “Hey Ampli” and ask for a song, a call or a place. It listens on the unit itself, so your voice never leaves the car." },
  { icon: Navigation, title: "Turn-by-turn in Haryanvi", body: "“Aage sulte haath mud jaiye.” Directions the way people in Haryana actually talk, with ulte and sulte haath." },
  { icon: Camera, title: "Speed camera alerts", body: "A spoken warning before speed cameras, only when you are over the limit if you want." },
  { icon: Siren, title: "SOS and crash check", body: "Send your location to family in one tap. Possible crash detection with a countdown, always in clear Hindi." },
  { icon: CreditCard, title: "FASTag and car papers", body: "Low FASTag balance warnings from your toll SMS, and reminders for insurance, PUC and service." },
  { icon: Fuel, title: "Mileage and expenses", body: "Log fuel and service, see your mileage, and know where you parked your car." },
  { icon: Palette, title: "Themes for your dashboard", body: "Five free home-screen themes, made for 1024×600 head units and bright sunlight." },
  { icon: Languages, title: "About 20 languages", body: "Haryanvi and Hindi first, plus English, Punjabi, Rajasthani, Marathi, Tamil, Telugu and more." },
];

const plans: Plan[] = [
  {
    name: "Free",
    price: "₹0",
    note: "forever",
    blurb: "The full car launcher with every voice and safety feature. No ads.",
    features: ["Haryanvi voices Bunty and Simran, offline", "“Hey Ampli” voice control and navigation", "Speed cameras, SOS, crash check", "FASTag, papers, mileage, where's my car", "5 home-screen themes"],
  },
  {
    name: "Car Hub Pro",
    price: "₹499",
    note: "once · lifetime",
    blurb: "For car lovers who want the engine data and every theme.",
    features: ["Everything in Free", "All 25 home-screen themes", "Engine data and fault codes (OBD-II adapter)", "Spoken overheating and battery warnings", "Bluetooth tyre pressure alerts, CSV export of expenses"],
    highlight: true,
  },
];

export function CarHub() {
  return (
    <section id="carhub" className="relative scroll-mt-24 overflow-hidden border-t border-line py-24">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[520px] bg-[radial-gradient(50%_60%_at_50%_0%,rgba(8,145,178,.22),transparent_70%)]" />
      <div className="container">
        <ProductHeading
          app="carhub"
          eyebrow="Ampli Car Hub"
          title={
            <>
              The world's first car that{" "}
              <span className="bg-[linear-gradient(90deg,#67E8F9,#0891B2)] bg-clip-text text-transparent">talks in Haryanvi.</span>
            </>
          }
          sub="Turn your Android head unit into a desi co-driver. It greets you, guides you and looks after you, in a real Haryanvi voice."
          accent={SOFT}
        />
        <BlurFade offset={24}>
          <HeadUnit />
        </BlurFade>

        <BlurFade className="mx-auto mt-16 max-w-3xl">
          <div className="relative overflow-hidden rounded-3xl border border-cyan-400/20 bg-gradient-to-br from-[#0B2733] to-raised p-8 text-center sm:p-10">
            <CarFront className="mx-auto text-cyan-300" size={32} />
            <p className="mt-4 text-2xl font-extrabold tracking-tight sm:text-3xl" lang="hi">
              म्हारा सपना: हरियाणा नै टॉप पै ले के जाणा सै।
            </p>
            <p className="mt-2 text-lg font-semibold text-cyan-200">Our dream: take Haryana to the top.</p>
            <p className="mx-auto mt-4 max-w-2xl text-ink-muted">
              Big apps talk to us in English. We built one that talks like our own people, in Haryanvi, with respect and a smile. Every line is family-safe and
              written the way people in Haryana really talk. Next stop: Punjabi, Rajasthani and every voice of India.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-2 text-sm">
              {["298 Haryanvi lines", "2 human voices", "100% offline voice", "Family-safe, always"].map((t) => (
                <span key={t} className="rounded-full border border-cyan-400/20 bg-cyan-500/10 px-3 py-1 text-cyan-100">
                  {t}
                </span>
              ))}
            </div>
          </div>
        </BlurFade>

        <div className="mt-16">
          <FeatureGrid features={features} accent={ACCENT} />
        </div>
        <BlurFade className="mt-6 flex items-center justify-center gap-2 text-sm text-ink-faint">
          <Gauge size={16} /> Works on Android head units 8.0 and newer, including 1024×600 screens.
        </BlurFade>
        <PlanCards plans={plans} accent={ACCENT} accentSoft={SOFT} appName="Ampli Car Hub" />
      </div>
    </section>
  );
}
