// Adapted from Magic UI (MIT, https://magicui.design), as listed on 21st.dev. Ported to Tailwind 3 + framer-motion.
"use client";
import { m, type MotionStyle } from "framer-motion";
import { cn } from "@/lib/utils";

interface BorderBeamProps {
  size?: number;
  duration?: number;
  delay?: number;
  colorFrom?: string;
  colorTo?: string;
  className?: string;
  borderWidth?: number;
}

export function BorderBeam({
  className,
  size = 90,
  delay = 0,
  duration = 7,
  colorFrom = "#FDA4AF",
  colorTo = "#E11D48",
  borderWidth = 1.5,
}: BorderBeamProps) {
  return (
    <div
      className="pointer-events-none absolute inset-0 rounded-[inherit] border-transparent [border-width:var(--bw)] [mask-clip:padding-box,border-box] [mask-composite:intersect] [mask-image:linear-gradient(transparent,transparent),linear-gradient(#000,#000)]"
      style={{ "--bw": `${borderWidth}px` } as React.CSSProperties}
    >
      <m.div
        className={cn("absolute aspect-square bg-gradient-to-l from-[var(--c-from)] via-[var(--c-to)] to-transparent", className)}
        style={
          {
            width: size,
            offsetPath: `rect(0 auto auto 0 round ${size}px)`,
            "--c-from": colorFrom,
            "--c-to": colorTo,
          } as MotionStyle
        }
        initial={{ offsetDistance: "0%" }}
        animate={{ offsetDistance: ["0%", "100%"] }}
        transition={{ repeat: Infinity, ease: "linear", duration, delay: -delay }}
      />
    </div>
  );
}
