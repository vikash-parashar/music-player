"use client";
import { BellOff, Link2Off, ShieldAlert, Siren } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { FeatureGrid, ProductHeading, type Feature } from "@/components/sections/product-blocks";
import { notifyHref } from "@/lib/site";

const ACCENT = "#0D9488";
const SOFT = "#5EEAD4";

const features: Feature[] = [
  { icon: ShieldAlert, title: "Caller card", body: "See who is calling and a spam warning on top of any Phone app, before you pick up." },
  { icon: Link2Off, title: "SMS scam check", body: "Fake KYC, prize and parcel links are flagged on the phone. Message text never leaves it." },
  { icon: BellOff, title: "Loan-app shield", body: "Step-by-step help if a loan app threatens you or messages your contacts." },
  { icon: Siren, title: "Digital arrest check", body: "Police never arrest anyone on a video call. Kavach explains this and opens the official complaint site." },
];

/** A scam call as Kavach shows it, drawn in code. */
function KavachPhone() {
  return (
    <div className="relative mx-auto w-[260px] sm:w-[290px]" aria-hidden>
      <div className="absolute -inset-10 -z-10 rounded-full bg-teal-600/25 blur-3xl" />
      <div className="rounded-[44px] border border-white/10 bg-[#141117] p-3">
        <div className="flex flex-col gap-3 rounded-[34px] bg-gradient-to-b from-[#0F3D3A] via-[#0b1f1e] to-night px-4 pb-6 pt-6 text-left">
          <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
            <p className="text-[11px] font-bold tracking-widest text-red-300">⚠ LIKELY SCAM</p>
            <p className="mt-1 text-lg font-bold">+91 98xxx xx210</p>
            <p className="text-sm text-ink-muted">Reported many times · “KYC update” calls</p>
          </div>
          <div className="flex gap-2.5 text-sm font-bold">
            <span className="flex-1 rounded-xl bg-red-600 py-2.5 text-center">Block</span>
            <span className="flex-1 rounded-xl bg-white/10 py-2.5 text-center">Answer</span>
          </div>
          <div className="rounded-2xl border border-teal-300/30 bg-white/5 p-4">
            <p className="text-[11px] font-bold tracking-widest text-teal-300">KAVACH</p>
            <p className="mt-1 text-[15px] font-semibold">Banks never ask for your OTP on a call.</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export function Kavach() {
  return (
    <section id="kavach" className="scroll-mt-24 py-24">
      <div className="container">
        <ProductHeading
          app="kavach"
          eyebrow="Ampli Kavach · in development"
          accent={SOFT}
          title="Stop scam calls before they reach your parents."
          sub="Kavach works on the phone and never uploads your contacts, unlike some caller-ID apps. Hindi first, built for Indian families."
        />
        <div className="grid items-center gap-12 lg:grid-cols-[1.2fr_.8fr]">
          <FeatureGrid features={features} accent={ACCENT} twoCols />
          <BlurFade delay={0.1}>
            <KavachPhone />
          </BlurFade>
        </div>
        <BlurFade className="mt-10 flex flex-wrap items-center justify-center gap-3 text-sm">
          <span className="rounded-full border border-line bg-raised px-4 py-2">Free with ads (never on call screens)</span>
          <span className="rounded-full border border-line bg-raised px-4 py-2">
            <b style={{ color: SOFT }}>Pro ₹149</b> once, no ads
          </span>
          <a href={notifyHref("Ampli Kavach")} className="rounded-full border border-teal-400/40 px-4 py-2 font-semibold hover:bg-white/5" style={{ color: SOFT }}>
            Notify me at launch
          </a>
        </BlurFade>
      </div>
    </section>
  );
}
