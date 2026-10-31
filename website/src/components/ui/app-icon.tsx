import { cn } from "@/lib/utils";
import type { AppId } from "@/lib/site";

/** The app's launcher icon. MP3 and Car Hub use the brand-kit icons; Naukri its saffron briefcase; Kavach a teal shield. */
export function AppIcon({ app, size = 56, className }: { app: AppId; size?: number; className?: string }) {
  const cls = cn("shrink-0 rounded-[22%]", className);
  if (app === "kavach") {
    return (
      <svg width={size} height={size} viewBox="0 0 64 64" className={cls} role="img" aria-label="Ampli Kavach">
        <rect width="64" height="64" rx="14" fill="#0D9488" />
        <path fill="#fff" d="M32 10l18 7v13c0 11.5-7.6 20.6-18 24-10.4-3.4-18-12.5-18-24V17l18-7z" />
        <path fill="none" stroke="#0D9488" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" d="M23.5 32.5l6 6 11-12" />
      </svg>
    );
  }
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
