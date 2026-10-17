import { Marquee } from "@/components/ui/marquee";
import { NumberTicker } from "@/components/ui/number-ticker";
import { BlurFade } from "@/components/ui/blur-fade";
import { SectionHeading } from "@/components/sections/section-heading";

const stats = [
  { value: 0, prefix: "₹", suffix: "", label: "monthly fee, in any app" },
  { value: 3, prefix: "", suffix: "", label: "apps for everyday India" },
  { value: 298, prefix: "", suffix: "", label: "Haryanvi lines in Car Hub" },
  { value: 100, prefix: "", suffix: "%", label: "offline music and voice" },
];

const words = ["राम राम", "संगीत", "सरकारी नौकरी", "இசை", "సంగీతం", "সঙ্গীত", "Haryanvi", "Bollywood", "Punjabi", "SSC", "Railway", "FASTag", "Hey Ampli", "Devotional"];

export function SocialProof() {
  return (
    <section className="border-y border-line bg-night-900/60 py-24">
      <div className="container">
        <SectionHeading eyebrow="Built for India" title="Made for the way India lives" sub="No subscriptions, no English-only screens. Apps that speak your language and respect your money." />
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {stats.map((s, i) => (
            <BlurFade key={s.label} delay={i * 0.06}>
              <div className="rounded-3xl border border-line bg-raised p-6 text-center transition-transform duration-300 hover:-translate-y-1">
                <p className="text-4xl font-extrabold tracking-tight sm:text-5xl">
                  {s.prefix}
                  <NumberTicker value={s.value} />
                  {s.suffix}
                </p>
                <p className="mt-2 text-sm text-ink-muted">{s.label}</p>
              </div>
            </BlurFade>
          ))}
        </div>
      </div>
      <div className="relative mt-14">
        <Marquee pauseOnHover className="[--duration:35s]">
          {words.map((w, i) => (
            <span key={i} className="rounded-full border border-line bg-raised px-5 py-2 text-lg text-ink-muted">
              {w}
            </span>
          ))}
        </Marquee>
        <div className="pointer-events-none absolute inset-y-0 left-0 w-24 bg-gradient-to-r from-night" aria-hidden />
        <div className="pointer-events-none absolute inset-y-0 right-0 w-24 bg-gradient-to-l from-night" aria-hidden />
      </div>
    </section>
  );
}
