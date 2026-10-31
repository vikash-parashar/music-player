"use client";
import { useEffect, useRef, useState } from "react";
import { showcase, type Showcase } from "@/lib/showcase";
import { Reveal } from "@/components/ui/reveal";

function Carousel({ app }: { app: Showcase }) {
  const [i, setI] = useState(0);
  const paused = useRef(false);
  const startX = useRef<number | null>(null);
  const n = app.shots.length;
  useEffect(() => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const t = setInterval(() => !paused.current && setI((v) => (v + 1) % n), 3200);
    return () => clearInterval(t);
  }, [n, i]);
  return (
    <Reveal className="show">
      <div
        className={app.landscape ? "dev land" : "dev"}
        onPointerEnter={() => (paused.current = true)}
        onPointerLeave={() => (paused.current = false)}
        onPointerDown={(e) => (startX.current = e.clientX)}
        onPointerUp={(e) => {
          if (startX.current !== null && Math.abs(e.clientX - startX.current) > 30) setI((v) => (v + (e.clientX < startX.current! ? 1 : -1) + n) % n);
          startX.current = null;
        }}
      >
        {app.shots.map((s, k) => (
          <img key={s.src} src={s.src} alt={`${app.name}: ${s.caption}`} loading="lazy" className={k === i ? "on" : undefined} />
        ))}
      </div>
      <p className="cap" aria-live="polite">
        {app.shots[i].caption}
      </p>
      <div className="dots">
        {app.shots.map((s, k) => (
          <button key={s.src} type="button" aria-label={`Show screen ${k + 1}: ${s.caption}`} aria-current={k === i} onClick={() => setI(k)} />
        ))}
      </div>
    </Reveal>
  );
}

export function AppShowcase() {
  return (
    <div id="apps">
      {showcase.map((app, ai) => (
        <section key={app.id} id={app.id} className={`app wrap t-${app.tint}${ai % 2 ? " flip" : ""}`} aria-labelledby={`${app.id}-title`}>
          <div className="app-grid">
            <Carousel app={app} />
            <div className="info">
              <Reveal className="head">
                <p className="eyebrow">
                  {String(ai + 1).padStart(2, "0")} · {app.name}
                </p>
                <h2 id={`${app.id}-title`} className="disp">
                  {app.short}
                </h2>
                <p>{app.intro}</p>
                <div className="meta">
                  <span className={`badge st-${app.status.tone}`}>{app.status.label}</span>
                  <span className="price">{app.price}</span>
                </div>
              </Reveal>
              {app.features.map((f, i) => (
                <Reveal key={f.title} className="feat" delay={(i % 2) * 0.08}>
                  <i aria-hidden>{f.icon}</i>
                  {f.pro && <span className="pro">Pro</span>}
                  <h3>{f.title}</h3>
                  <p>{f.body}</p>
                </Reveal>
              ))}
            </div>
          </div>
        </section>
      ))}
    </div>
  );
}
