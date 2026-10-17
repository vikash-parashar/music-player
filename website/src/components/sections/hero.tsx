import { ArrowRight, Download } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { ShimmerLink } from "@/components/ui/shimmer-button";
import { PhoneMockup } from "@/components/sections/phone-mockup";
import { site } from "@/lib/site";

export function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-[-20%] -z-10 h-[600px] bg-[radial-gradient(60%_50%_at_70%_30%,rgba(225,29,72,.25),transparent_70%)]"
      />
      <div className="container grid items-center gap-14 pb-20 pt-10 md:grid-cols-[1.15fr_.85fr] md:pb-28 md:pt-16">
        <div>
          <BlurFade inView={false} visibleFirst>
            <span className="inline-flex items-center gap-2 rounded-full border border-line bg-raised/60 px-3 py-1 text-xs text-ink-muted">
              <span className="size-1.5 rounded-full bg-rose-400" /> Ampli MP3 Player for Android · Made in India
            </span>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.08}>
            <h1 className="mt-5 text-[2.6rem] font-extrabold leading-[1.05] tracking-tight sm:text-6xl">
              Your music.
              <br />
              <span className="bg-brand-text bg-clip-text text-transparent">No monthly fee.</span>
            </h1>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.16}>
            <p className="mt-5 max-w-xl text-lg text-ink-muted sm:text-xl">
              A fast, beautiful offline player for the songs already on your phone. No account, no streaming bill. Pay once, play forever.
            </p>
          </BlurFade>
          <BlurFade inView={false} visibleFirst delay={0.24}>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <ShimmerLink href={site.playUrl}>
                <Download size={18} /> Get it on Google Play
              </ShimmerLink>
              <a
                href="#features"
                className="group inline-flex items-center gap-2 rounded-full border border-line px-6 py-3 font-semibold text-ink transition-colors hover:border-rose-400/50 hover:bg-white/5"
              >
                See features <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
              </a>
            </div>
            <p className="mt-4 text-sm text-ink-faint">Free download · Ampli Pro ₹199 one time · Coming soon to Google Play</p>
          </BlurFade>
        </div>
        <BlurFade inView={false} visibleFirst delay={0.2} offset={24}>
          <PhoneMockup />
        </BlurFade>
      </div>
    </section>
  );
}
