"use client";
import { m, useReducedMotion } from "framer-motion";
import { useNight } from "@/components/theme";

/**
 * Entrance motion that follows the look: day = Bento Pop (spring up from small),
 * night = Text Mask Zoom (settle down from large and blurred).
 */
export function Reveal({ children, className, delay = 0, id }: { children: React.ReactNode; className?: string; delay?: number; id?: string }) {
  const night = useNight();
  const reduce = useReducedMotion();
  if (reduce) return <div className={className} id={id}>{children}</div>;
  return (
    <m.div
      key={night ? "n" : "d"}
      id={id}
      className={className}
      // Blur runs through a CSS variable: animating `filter` directly trips a Framer Motion keyframe bug.
      style={night ? ({ filter: "blur(calc(var(--rb) * 1px))" } as React.CSSProperties) : undefined}
      initial={night ? { opacity: 0, scale: 1.15, "--rb": 8 } : { opacity: 0, scale: 0.6 }}
      whileInView={night ? { opacity: 1, scale: 1, "--rb": 0 } : { opacity: 1, scale: 1 }}
      viewport={{ once: true, amount: 0.15 }}
      transition={night ? { duration: 1.2, ease: [0.16, 1, 0.3, 1], delay } : { type: "spring", stiffness: 260, damping: 17, delay }}
    >
      {children}
    </m.div>
  );
}
