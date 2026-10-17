import Link from "next/link";
import { Mail } from "lucide-react";
import { ShimmerLink } from "@/components/ui/shimmer-button";
import { BlurFade } from "@/components/ui/blur-fade";
import { footerLinks, notifyHref, site } from "@/lib/site";

export function Footer() {
  return (
    <footer className="border-t border-line">
      <div className="container py-20">
        <BlurFade className="relative overflow-hidden rounded-3xl border border-line bg-gradient-to-br from-slate-700/50 to-rose-600/30 p-10 text-center sm:p-14">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">Desi apps deserve a chance.</h2>
          <p className="mx-auto mt-3 max-w-lg text-ink-muted">Get an email the day our apps reach Google Play, and be one of the first to try them.</p>
          <div className="mt-8 flex justify-center">
            <ShimmerLink href={notifyHref("each Ampli app")}>Notify me at launch</ShimmerLink>
          </div>
        </BlurFade>
      </div>
      <div className="container flex flex-col gap-6 border-t border-line py-10 text-sm text-ink-faint md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-2.5">
          <img src="/img/icon-192.png" alt="" width={28} height={28} className="rounded-lg" />
          <span>© 2026 {site.company}, India</span>
        </div>
        <ul className="flex flex-wrap gap-x-6 gap-y-2">
          {footerLinks.map((n) => (
            <li key={n.href}>
              <Link href={n.href} className="hover:text-ink">
                {n.label}
              </Link>
            </li>
          ))}
          <li>
            <a href={`mailto:${site.email}`} className="inline-flex items-center gap-1.5 hover:text-ink">
              <Mail size={14} /> {site.email}
            </a>
          </li>
        </ul>
      </div>
    </footer>
  );
}
