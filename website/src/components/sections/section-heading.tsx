import { BlurFade } from "@/components/ui/blur-fade";

export function SectionHeading({ eyebrow, title, sub }: { eyebrow: string; title: string; sub?: string }) {
  return (
    <BlurFade className="mx-auto mb-12 max-w-2xl text-center">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-rose-300">{eyebrow}</p>
      <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">{title}</h2>
      {sub && <p className="mt-3 text-ink-muted">{sub}</p>}
    </BlurFade>
  );
}
