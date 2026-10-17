"use client";
import { useEffect, useState } from "react";
import { AnimatePresence, m, useReducedMotion } from "framer-motion";
import { BadgeCheck, BellRing, CalendarClock, ExternalLink, Languages, MapPin, Sparkles, ClipboardCheck, BarChart3 } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { FeatureGrid, PlanCards, ProductHeading, type Feature, type Plan } from "@/components/sections/product-blocks";

const ACCENT = "#C2410C";
const SOFT = "#FDBA74";

const jobs = [
  { title: "HSSC CET Group C", org: "Haryana Staff Selection Commission", last: "12 Nov", ok: true },
  { title: "SSC GD Constable", org: "Staff Selection Commission", last: "28 Nov", ok: true },
  { title: "UP Police Constable", org: "UPPRPB", last: "5 Dec", ok: false },
  { title: "RRB NTPC Graduate", org: "Railway Recruitment Boards", last: "19 Nov", ok: true },
];

const stages = ["Applied", "Admit card", "Exam", "Result"];

function NaukriPhone() {
  const reduce = useReducedMotion();
  const [top, setTop] = useState(0);
  const [stage, setStage] = useState(1);
  useEffect(() => {
    if (reduce) return;
    const t = setInterval(() => {
      setTop((v) => (v + 1) % jobs.length);
      setStage((v) => (v % 3) + 1);
    }, 3200);
    return () => clearInterval(t);
  }, [reduce]);
  const order = [0, 1, 2].map((k) => jobs[(top + k) % jobs.length]);
  return (
    <div className="relative mx-auto w-[260px] sm:w-[290px]">
      <div className="absolute -inset-10 -z-10 rounded-full bg-orange-600/25 blur-3xl" aria-hidden />
      <div className="rounded-[44px] border border-white/10 bg-[#141117] p-3">
        <div className="overflow-hidden rounded-[34px] bg-gradient-to-b from-[#1E1B4B] via-[#17153a] to-night pb-5 text-left">
          <div className="flex items-center justify-between px-5 pt-4 text-[11px] text-indigo-200/70">
            <span>9:41</span>
            <span className="h-5 w-20 rounded-full bg-black/50" />
            <span>100%</span>
          </div>
          <div className="px-4 pt-4">
            <p className="text-[11px] text-indigo-200/80" lang="hi">
              नमस्ते, रवि
            </p>
            <p className="text-lg font-bold" lang="hi">
              आज 3 नई नौकरी आपके लिए
            </p>
          </div>
          <div className="relative mt-3 h-[236px] px-4">
            <AnimatePresence initial={false}>
              {order.map((j, k) => (
                <m.div
                  key={j.title}
                  layout
                  initial={{ opacity: 0, y: 30, scale: 0.96 }}
                  animate={{ opacity: 1, y: k * 76, scale: 1 }}
                  exit={{ opacity: 0, x: -60 }}
                  transition={{ type: "spring", stiffness: 260, damping: 26 }}
                  className="absolute inset-x-4 rounded-2xl border border-white/10 bg-white/[.06] p-3 backdrop-blur"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold">{j.title}</p>
                      <p className="truncate text-[11px] text-indigo-200/70">{j.org}</p>
                    </div>
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                        j.ok ? "bg-emerald-500/20 text-emerald-300" : "bg-amber-500/20 text-amber-200"
                      }`}
                    >
                      {j.ok ? "Eligible" : "Check"}
                    </span>
                  </div>
                  <p className="mt-1.5 text-[11px] text-orange-200">Last date {j.last}</p>
                </m.div>
              ))}
            </AnimatePresence>
          </div>
          <div className="mx-4 mt-1 rounded-2xl bg-[#C2410C] p-3">
            <p className="text-[11px] font-semibold">SSC GD Constable · applied</p>
            <div className="mt-2.5 flex items-center">
              {stages.map((s, k) => (
                <div key={s} className="flex flex-1 flex-col items-center last:flex-none">
                  <div className="flex w-full items-center">
                    <m.span
                      className="size-3 shrink-0 rounded-full border-2 border-white"
                      animate={{ backgroundColor: k <= stage ? "#FFFFFF" : "rgba(255,255,255,0)" }}
                    />
                    {k < stages.length - 1 && (
                      <span className="h-0.5 flex-1 bg-white/30">
                        <m.span className="block h-full bg-white" animate={{ width: k < stage ? "100%" : "0%" }} transition={{ duration: 0.5 }} />
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-1.5 flex justify-between text-[9px] text-orange-100">
              {stages.map((s) => (
                <span key={s}>{s}</span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

const features: Feature[] = [
  { icon: BadgeCheck, title: "Eligibility check on every job", body: "Reads your age, category and marks, adds category age relaxation, and marks each job “Eligible” or “Check”." },
  { icon: BellRing, title: "Daily jobs matched to you", body: "New jobs that fit your profile every day, in the app and by email. WhatsApp and SMS later." },
  { icon: CalendarClock, title: "Never miss a last date", body: "Reminders 3 days and 1 day before the last date, so you apply in time." },
  { icon: ClipboardCheck, title: "Mark as applied, track every stage", body: "After you apply, Ampli Naukri alerts you about the admit card, exam, physical, interview, answer key and result." },
  { icon: ExternalLink, title: "Official links only", body: "Every job links to the official notice and apply page. Ampli Naukri is not a government app and never asks you to pay to apply." },
  { icon: Languages, title: "Hindi first", body: "Made for Hindi readers first, with English too. Simple words, big text." },
  { icon: MapPin, title: "Your state first", body: "Haryana, UP, Rajasthan and Delhi at launch, plus central exams: SSC, Railway, Banking and Defence." },
  { icon: BarChart3, title: "Coming: exam stats", body: "Five years of vacancies per exam, with an estimate of your chances." },
  { icon: Sparkles, title: "Coming: AI doubt-solver", body: "Ask study doubts by text or voice. 10 doubts a day free, more after a short ad." },
];

const plans: Plan[] = [
  {
    name: "Job seekers",
    price: "₹0",
    note: "forever",
    blurb: "Students never pay. There is no Pro plan for job seekers.",
    features: ["Every alert and the eligibility check", "Last-date and stage reminders", "Hindi and English, official links", "AI doubt-solver: 10 doubts a day free (coming)", "A small ad at the bottom pays for it"],
    highlight: true,
    badge: "Always free",
  },
  {
    name: "Institutes and employers",
    price: "₹499+",
    note: "per job post",
    blurb: "Paid listings that help keep the app free for students.",
    features: ["Post a job for ₹499 to ₹999, first post free", "Coaching institutes: demo-class leads, only with consent", "Sponsored mock tests, always labelled “Sponsored”", "Coming after launch"],
  },
];

export function Naukri() {
  return (
    <section id="naukri" className="relative scroll-mt-24 overflow-hidden border-t border-line py-24">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[520px] bg-[radial-gradient(50%_60%_at_50%_0%,rgba(49,46,129,.45),transparent_70%)]" />
      <div className="container">
        <ProductHeading
          app="naukri"
          eyebrow="Ampli Naukri"
          title={
            <>
              Free sarkari job alerts that{" "}
              <span className="bg-[linear-gradient(90deg,#FDBA74,#EA580C)] bg-clip-text text-transparent">check if you are eligible.</span>
            </>
          }
          sub="सरकारी नौकरी की फ़्री अलर्ट, साथ में यह भी कि आप योग्य हैं या नहीं। Ampli Naukri finds the jobs you can actually apply for, then walks with you to the result."
          accent={SOFT}
        />
        <div className="grid items-center gap-12 lg:grid-cols-[.8fr_1.2fr]">
          <BlurFade offset={24}>
            <NaukriPhone />
          </BlurFade>
          <FeatureGrid features={features.slice(0, 6)} accent={ACCENT} twoCols />
        </div>
        <div className="mt-4">
          <FeatureGrid features={features.slice(6)} accent={ACCENT} />
        </div>
        <PlanCards plans={plans} accent={ACCENT} accentSoft={SOFT} appName="Ampli Naukri" />
      </div>
    </section>
  );
}
