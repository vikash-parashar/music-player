// Adapted from Magic UI (MIT, https://magicui.design), as listed on 21st.dev. Ported to Tailwind 3 + framer-motion.
import { type ComponentPropsWithoutRef } from "react";
import { cn } from "@/lib/utils";

interface MarqueeProps extends ComponentPropsWithoutRef<"div"> {
  reverse?: boolean;
  pauseOnHover?: boolean;
  repeat?: number;
}

export function Marquee({ className, reverse = false, pauseOnHover = false, children, repeat = 3, ...props }: MarqueeProps) {
  return (
    <div
      {...props}
      className={cn("group flex flex-row gap-[var(--gap)] overflow-hidden p-2 [--duration:40s] [--gap:1rem]", className)}
    >
      {Array.from({ length: repeat }).map((_, i) => (
        <div
          key={i}
          aria-hidden={i > 0 || undefined}
          className={cn(
            "flex shrink-0 animate-marquee flex-row justify-around gap-[var(--gap)] motion-reduce:[animation-play-state:paused]",
            pauseOnHover && "group-hover:[animation-play-state:paused]",
            reverse && "[animation-direction:reverse]",
          )}
        >
          {children}
        </div>
      ))}
    </div>
  );
}
