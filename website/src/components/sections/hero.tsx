"use client";
import { m, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { Reveal } from "@/components/ui/reveal";
import { useNight } from "@/components/theme";

/** Hero. At night the headline zooms toward the viewer and dissolves as you scroll (Text Mask Zoom). */
export function Hero() {
  const night = useNight();
  const reduce = useReducedMotion();
  const { scrollY } = useScroll();
  const zoom = !reduce && night;
  const progress = (y: number) => Math.min(1, y / ((typeof window === "undefined" ? 800 : window.innerHeight) * 0.8));
  const scale = useTransform(scrollY, (y) => (zoom ? 1 + progress(y) ** 2 * 6 : 1));
  const opacity = useTransform(scrollY, (y) => (zoom ? Math.max(0, 1 - progress(y) * 1.3) : 1));

  return (
    <section className="wrap hero" aria-labelledby="hero-title">
      <div>
        <Reveal>
          <p className="eyebrow">An Ampli product · by Vikash Parashar</p>
        </Reveal>
        <Reveal delay={0.05}>
          <m.h1 id="hero-title" className="disp hero-title" style={{ scale, opacity, transformOrigin: "20% 50%" }}>
            Four apps.
            <br />
            <em>Zero rent.</em>
          </m.h1>
        </Reveal>
        <Reveal delay={0.1}>
          <p className="hero-sub">
            Music, driving, scam safety and government jobs. Four Android apps with one-time Pro upgrades instead of monthly fees. Hindi first, made for the world.
          </p>
        </Reveal>
        <Reveal delay={0.15} className="ctas">
          <a className="pill solid" href="#music">
            Explore the apps →
          </a>
          <a className="pill" href="#pricing">
            See pricing
          </a>
        </Reveal>
      </div>
      <Reveal className="fan" delay={0.1}>
        <div className="ph p1 bob">
          <img src="/img/apps/kv-1.webp" alt="Ampli Kavach warning when a scam number calls" width={420} height={863} />
        </div>
        <div className="ph p2 bob">
          <img src="/img/apps/mp3-0.webp" alt="Ampli MP3 record player" width={540} height={960} />
        </div>
        <div className="ph p3 bob">
          <img src="/img/apps/nk-0.webp" alt="Ampli Naukri jobs that match you today" width={420} height={884} />
        </div>
        <div className="car-screen bob">
          <img src="/img/apps/car-6.webp" alt="Ampli Car Hub FASTag balance" width={820} height={480} />
        </div>
      </Reveal>
    </section>
  );
}
