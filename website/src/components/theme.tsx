"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { cn } from "@/lib/utils";


type Mode = "auto" | "light" | "dark";

function subscribe(cb: () => void) {
  const mo = new MutationObserver(cb);
  mo.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => mo.disconnect();
}

/** True when the site shows its night look (Text Mask Zoom); false for the day look (Bento Pop). */
export function useNight() {
  return useSyncExternalStore(subscribe, () => document.documentElement.dataset.theme === "dark", () => false);
}

function apply(mode: Mode) {
  const dark = mode === "dark" || (mode === "auto" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
}

export function ModeToggle({ className }: { className?: string }) {
  const [mode, setMode] = useState<Mode>("auto");
  useEffect(() => {
    let saved: Mode = "auto";
    try {
      saved = (localStorage.getItem("ampli-mode") as Mode) || "auto";
    } catch {}
    setMode(saved);
  }, []);
  useEffect(() => {
    if (mode !== "auto") return;
    const mq = matchMedia("(prefers-color-scheme: dark)");
    const on = () => apply("auto");
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, [mode]);
  const pick = (m: Mode) => {
    setMode(m);
    try {
      localStorage.setItem("ampli-mode", m);
    } catch {}
    apply(m);
  };
  const opts: [Mode, string][] = [
    ["auto", "Auto"],
    ["light", "☀ Day"],
    ["dark", "☾ Night"],
  ];
  return (
    <div role="group" aria-label="Day or night look" className={cn("mode-toggle", className)}>
      {opts.map(([m, label]) => (
        <button key={m} type="button" aria-pressed={mode === m} onClick={() => pick(m)}>
          {label}
        </button>
      ))}
    </div>
  );
}
