"use client";
import { LazyMotion, domAnimation } from "framer-motion";

/** Loads only the Framer Motion features we use (smaller JS, better Lighthouse). Components use `m.*`. */
export function MotionProvider({ children }: { children: React.ReactNode }) {
  return (
    <LazyMotion features={domAnimation} strict>
      {children}
    </LazyMotion>
  );
}
