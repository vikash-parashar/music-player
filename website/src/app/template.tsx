"use client";
import { m } from "framer-motion";

/** Page transition: every route fades and lifts in. template.tsx re-mounts on each navigation. */
export default function Template({ children }: { children: React.ReactNode }) {
  return (
    <m.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, ease: "easeOut" }}>
      {children}
    </m.div>
  );
}
