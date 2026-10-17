"use client";
import { m, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { Pause, SkipBack, SkipForward, Heart, Shuffle } from "lucide-react";

/** Stylised Ampli "Now Playing" screen, drawn in code so the hero needs no heavy screenshot. */
export function PhoneMockup() {
  const reduce = useReducedMotion();
  const { scrollY } = useScroll();
  const y = useTransform(scrollY, [0, 600], [0, reduce ? 0 : -60]);
  const rotate = useTransform(scrollY, [0, 600], [0, reduce ? 0 : -4]);

  return (
    <m.div style={{ y, rotate }} className="relative mx-auto w-[260px] sm:w-[290px]">
      <div className="absolute -inset-10 -z-10 rounded-full bg-rose-600/30 blur-3xl" aria-hidden />
      <div className="rounded-[44px] border border-white/10 bg-[#141117] p-3 shadow-glow">
        <div className="overflow-hidden rounded-[34px] bg-gradient-to-b from-[#2a1f2c] via-night to-night">
          <div className="flex items-center justify-between px-5 pt-4 text-[11px] text-ink-faint">
            <span>9:41</span>
            <span className="h-5 w-20 rounded-full bg-black/60" />
            <span>100%</span>
          </div>
          <div className="px-5 pb-6 pt-5">
            <p className="text-center text-[11px] uppercase tracking-[0.18em] text-ink-faint">Now playing</p>
            {/* The app's record player: a black record with the song poster as its label. */}
            <div className="relative mx-auto mt-4 aspect-square w-full rounded-3xl bg-[#1C1820] p-3">
              <m.div
                animate={reduce ? undefined : { rotate: 360 }}
                transition={{ duration: 2.4, repeat: Infinity, ease: "linear" }}
                className="relative size-full rounded-full bg-[repeating-radial-gradient(circle,#121014_0_2px,#1a171d_2px_4px)] shadow-[inset_0_0_0_1px_rgba(225,29,72,.5)]"
              >
                <img
                  src="/img/ampli-mp3-icon-384.webp"
                  alt=""
                  width={384}
                  height={384}
                  className="absolute left-1/2 top-1/2 size-[38%] -translate-x-1/2 -translate-y-1/2 rounded-full object-cover"
                />
                <span className="absolute left-1/2 top-1/2 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#E4E1E6]" />
              </m.div>
              <div className="pointer-events-none absolute inset-3 rounded-full bg-[conic-gradient(from_20deg,transparent_0deg,rgba(255,255,255,.12)_25deg,transparent_55deg,transparent_180deg,rgba(255,255,255,.08)_205deg,transparent_235deg)]" />
              <span className="absolute bottom-6 right-5 h-1.5 w-[44%] origin-right -rotate-[28deg] rounded-full bg-[#D6D1DA]">
                <span className="absolute -left-1 -top-1 h-3.5 w-5 rounded bg-rose-600" />
              </span>
            </div>
            <div className="mt-5 flex items-center justify-between">
              <div>
                <p className="font-semibold">Tum Hi Ho</p>
                <p className="text-xs text-ink-muted">Your offline library</p>
              </div>
              <Heart size={18} className="fill-rose-500 text-rose-500" />
            </div>
            <div className="mt-4 flex h-8 items-end justify-center gap-1" aria-hidden>
              {Array.from({ length: 18 }).map((_, i) => (
                <span
                  key={i}
                  className="w-1.5 origin-bottom animate-eq-bar rounded-full bg-gradient-to-t from-rose-600 to-rose-300 motion-reduce:animate-none"
                  style={{ height: `${40 + ((i * 37) % 60)}%`, animationDelay: `${(i % 6) * 0.12}s` }}
                />
              ))}
            </div>
            <div className="mt-3 h-1 rounded-full bg-white/10">
              <div className="h-1 w-2/5 rounded-full bg-rose-400" />
            </div>
            <div className="mt-5 flex items-center justify-between text-ink">
              <Shuffle size={18} className="text-ink-faint" />
              <SkipBack size={22} />
              <span className="grid size-14 place-items-center rounded-full bg-brand shadow-glow">
                <Pause size={24} className="fill-white" />
              </span>
              <SkipForward size={22} />
              <span className="text-[11px] font-semibold text-rose-300">EQ</span>
            </div>
          </div>
        </div>
      </div>
    </m.div>
  );
}
