"use client";
import { m, useReducedMotion, useScroll, useSpring, useTransform, useVelocity } from "framer-motion";

/** Kinetic Type bands: two marquees running opposite ways that lean with scroll speed. */
export function Bands() {
  const reduce = useReducedMotion();
  const { scrollY } = useScroll();
  const velocity = useSpring(useVelocity(scrollY), { damping: 40, stiffness: 300 });
  const skewY = useTransform(velocity, [-2500, 0, 2500], reduce ? [0, 0, 0] : [-7, 0, 7]);
  const a = "Ampli MP3 · Car Hub · Kavach · Naukri · Pay once · ";
  const b = "No subscriptions · Hindi first · Made in India · Offline first · ";
  return (
    <m.div className="bands" style={{ skewY }} aria-hidden>
      <div className="band">
        <span>{a}</span>
        <span>{a}</span>
      </div>
      <div className="band rev">
        <span>{b}</span>
        <span>{b}</span>
      </div>
    </m.div>
  );
}
