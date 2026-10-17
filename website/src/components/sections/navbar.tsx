"use client";
import { useState } from "react";
import Link from "next/link";
import { AnimatePresence, m, useMotionValueEvent, useScroll } from "framer-motion";
import { Menu, X } from "lucide-react";
import { nav, site } from "@/lib/site";
import { cn } from "@/lib/utils";

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const { scrollY } = useScroll();
  useMotionValueEvent(scrollY, "change", (y) => setScrolled(y > 12));

  return (
    <header className="sticky top-0 z-50">
      <m.div
        initial={false}
        animate={{ paddingTop: scrolled ? 10 : 18, paddingBottom: scrolled ? 10 : 18 }}
        transition={{ duration: 0.25, ease: "easeOut" }}
        className={cn(
          "border-b transition-colors duration-300",
          scrolled ? "border-line bg-night/80 backdrop-blur-xl" : "border-transparent bg-transparent",
        )}
      >
        <nav className="container flex items-center justify-between" aria-label="Main">
          <Link href="/" className="flex items-center gap-2.5 text-lg font-bold tracking-tight">
            <img src="/img/icon-192.png" alt="" width={34} height={34} className="rounded-[9px]" />
            {site.name}
          </Link>

          <ul className="hidden items-center gap-0.5 lg:flex">
            {nav.map((n) => (
              <li key={n.href}>
                <Link
                  href={n.href}
                  className="relative rounded-full px-3.5 py-2 text-sm text-ink-muted transition-colors hover:bg-white/5 hover:text-ink"
                >
                  {n.label}
                </Link>
              </li>
            ))}
          </ul>

          <div className="flex items-center gap-2">
            <a
              href="/#support"
              className="hidden rounded-full bg-brand px-4 py-2 text-sm font-semibold text-white transition-transform duration-200 hover:scale-105 lg:inline-flex"
            >
              Support Ampli
            </a>
            <button
              type="button"
              aria-label={open ? "Close menu" : "Open menu"}
              aria-expanded={open}
              onClick={() => setOpen((v) => !v)}
              className="grid size-10 place-items-center rounded-full text-ink hover:bg-white/5 lg:hidden"
            >
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </nav>

        <AnimatePresence>
          {open && (
            <m.ul
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.25, ease: "easeOut" }}
              className="container overflow-hidden lg:hidden"
            >
              {[...nav, { href: "/privacy/", label: "Privacy" }].map((n, i) => (
                <m.li key={n.href} initial={{ x: -8, opacity: 0 }} animate={{ x: 0, opacity: 1 }} transition={{ delay: 0.04 * i }}>
                  <Link href={n.href} onClick={() => setOpen(false)} className="block border-b border-line py-3.5 text-ink-muted hover:text-ink">
                    {n.label}
                  </Link>
                </m.li>
              ))}
            </m.ul>
          )}
        </AnimatePresence>
      </m.div>
    </header>
  );
}
