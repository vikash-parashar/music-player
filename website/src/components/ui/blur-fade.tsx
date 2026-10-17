// Adapted from Magic UI (MIT, https://magicui.design), as listed on 21st.dev. Ported to Tailwind 3 + framer-motion.
"use client";
import { useRef } from "react";
import { m, useInView, useReducedMotion, type Variants } from "framer-motion";

interface BlurFadeProps {
  children: React.ReactNode;
  className?: string;
  duration?: number;
  delay?: number;
  offset?: number;
  /** Animate when scrolled into view (scroll reveal). */
  inView?: boolean;
  blur?: string;
  /** Keep the element visible before JS loads (for above-the-fold text): animate blur and lift only. */
  visibleFirst?: boolean;
}

export function BlurFade({ children, className, duration = 0.5, delay = 0, offset = 12, inView = true, blur = "6px", visibleFirst = false }: BlurFadeProps) {
  const ref = useRef(null);
  const seen = useInView(ref, { once: true, margin: "-60px" });
  const reduce = useReducedMotion();
  const show = !inView || seen;
  const variants: Variants = reduce
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : {
        hidden: { y: offset, opacity: visibleFirst ? 1 : 0, filter: `blur(${blur})` },
        visible: { y: 0, opacity: 1, filter: "blur(0px)" },
      };
  return (
    <m.div
      ref={ref}
      initial="hidden"
      animate={show ? "visible" : "hidden"}
      variants={variants}
      transition={{ delay: 0.04 + delay, duration, ease: "easeOut" }}
      className={className}
    >
      {children}
    </m.div>
  );
}
