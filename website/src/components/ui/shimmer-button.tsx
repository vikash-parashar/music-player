// Adapted from Magic UI (MIT, https://magicui.design), as listed on 21st.dev. Ported to Tailwind 3 + framer-motion.
import { type CSSProperties, type ComponentPropsWithoutRef } from "react";
import { cn } from "@/lib/utils";

interface ShimmerLinkProps extends ComponentPropsWithoutRef<"a"> {
  shimmerColor?: string;
  background?: string;
}

/** Magic UI's Shimmer Button, rendered as a link so CTAs work without JavaScript. */
export function ShimmerLink({
  shimmerColor = "#FDA4AF",
  background = "linear-gradient(135deg,#334155,#E11D48)",
  className,
  children,
  ...props
}: ShimmerLinkProps) {
  return (
    <a
      style={{ "--spread": "90deg", "--shimmer-color": shimmerColor, "--speed": "3s", "--cut": "0.06em", "--bg": background } as CSSProperties}
      className={cn(
        "group relative z-0 inline-flex items-center justify-center gap-2 overflow-hidden whitespace-nowrap rounded-full border border-white/10 px-6 py-3 font-semibold text-white [background:var(--bg)]",
        "transform-gpu transition-transform duration-300 ease-out hover:scale-[1.03] active:translate-y-px focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-300",
        className,
      )}
      {...props}
    >
      <span className="absolute inset-0 -z-30 overflow-visible blur-[2px] [container-type:size]">
        <span className="absolute inset-0 aspect-square h-[100cqh] animate-shimmer-slide motion-reduce:hidden">
          <span className="absolute -inset-full animate-spin-around [background:conic-gradient(from_calc(270deg-(var(--spread)*0.5)),transparent_0,var(--shimmer-color)_var(--spread),transparent_var(--spread))]" />
        </span>
      </span>
      {children}
      <span className="absolute inset-0 rounded-full shadow-[inset_0_-8px_10px_#ffffff1f] transition-all duration-300 group-hover:shadow-[inset_0_-6px_10px_#ffffff3f]" />
      <span className="absolute inset-[var(--cut)] -z-20 rounded-full [background:var(--bg)]" />
    </a>
  );
}
