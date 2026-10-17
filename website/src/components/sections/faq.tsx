"use client";
import { useState } from "react";
import { AnimatePresence, m } from "framer-motion";
import { Plus } from "lucide-react";
import { faqs } from "@/lib/site";
import { BlurFade } from "@/components/ui/blur-fade";
import { SectionHeading } from "@/components/sections/section-heading";

// FAQPage structured data, so Google can show these answers as rich results.
const faqJsonLd = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  mainEntity: faqs.map((f) => ({ "@type": "Question", name: f.q, acceptedAnswer: { "@type": "Answer", text: f.a } })),
};

export function FAQ() {
  const [open, setOpen] = useState<number | null>(0);
  return (
    <section id="faq" className="scroll-mt-24 border-t border-line py-24">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(faqJsonLd) }} />
      <div className="container max-w-3xl">
        <SectionHeading eyebrow="FAQ" title="Questions, answered" />
        <div className="divide-y divide-line rounded-3xl border border-line bg-raised">
          {faqs.map((f, i) => {
            const isOpen = open === i;
            return (
              <BlurFade key={f.q} delay={i * 0.04}>
                <h3>
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    aria-controls={`faq-${i}`}
                    onClick={() => setOpen(isOpen ? null : i)}
                    className="flex w-full items-center justify-between gap-4 px-6 py-5 text-left font-medium transition-colors hover:text-rose-300"
                  >
                    {f.q}
                    <m.span animate={{ rotate: isOpen ? 45 : 0 }} transition={{ duration: 0.2 }} className="shrink-0 text-rose-300">
                      <Plus size={20} />
                    </m.span>
                  </button>
                </h3>
                <AnimatePresence initial={false}>
                  {isOpen && (
                    <m.div
                      id={`faq-${i}`}
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25, ease: "easeOut" }}
                      className="overflow-hidden"
                    >
                      <p className="px-6 pb-5 leading-relaxed text-ink-muted">{f.a}</p>
                    </m.div>
                  )}
                </AnimatePresence>
              </BlurFade>
            );
          })}
        </div>
      </div>
    </section>
  );
}
