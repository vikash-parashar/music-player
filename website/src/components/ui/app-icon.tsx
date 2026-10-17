import { cn } from "@/lib/utils";

/** The app's launcher icon. MP3 and Car Hub use the brand-kit icons; Naukri uses its saffron briefcase. */
export function AppIcon({ app, size = 56, className }: { app: "mp3" | "carhub" | "naukri"; size?: number; className?: string }) {
  const cls = cn("shrink-0 rounded-[22%]", className);
  if (app === "naukri") {
    return (
      <svg width={size} height={size} viewBox="0 0 64 64" className={cls} role="img" aria-label="Ampli Naukri">
        <rect width="64" height="64" rx="14" fill="#EA580C" />
        <path
          fill="#fff"
          d="M25 16h14a4 4 0 0 1 4 4v4h7a4 4 0 0 1 4 4v18a4 4 0 0 1-4 4H14a4 4 0 0 1-4-4V28a4 4 0 0 1 4-4h7v-4a4 4 0 0 1 4-4zm1 8h12v-3H26v3z"
        />
      </svg>
    );
  }
  return (
    <img
      src={`/img/ampli-${app}-icon-192.webp`}
      alt={app === "mp3" ? "Ampli MP3 Player" : "Ampli Car Hub"}
      width={size}
      height={size}
      className={cls}
    />
  );
}
