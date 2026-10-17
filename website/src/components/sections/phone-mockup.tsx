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
            <m.div
              animate={reduce ? undefined : { scale: [1, 1.02, 1] }}
              transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
              className="mx-auto mt-4 aspect-square w-full overflow-hidden rounded-3xl bg-brand"
            >
              <img src="/img/ampli-mp3-icon-384.webp" alt="" width={384} height={384} className="size-full object-cover" />
            </m.div>
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
