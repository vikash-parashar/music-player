import { SectionHeading } from "@/components/sections/section-heading";
import { products, statusUpdated, type Product } from "@/lib/site";

const tone: Record<Product["statusTone"], string> = {
  live: "border-green-900 bg-green-950/60 text-green-300",
  test: "border-amber-900 bg-amber-950/50 text-amber-300",
  dev: "border-sky-900 bg-sky-950/50 text-sky-300",
};

const rows: Record<string, { free: string; pro: string; platform: string }> = {
  music: {
    free: "Full player with ads, Car and Record styles",
    pro: "₹199: no ads, all 10 styles and car light shows, Pro equalizer, Hi-Res, network folders",
    platform: "Android phones",
  },
  carhub: {
    free: "Voice control, fuel/CNG/EV finder, speed cameras, SOS, no ads",
    pro: "₹1,499 for 60 days after launch, then ₹2,500: red-light cameras, average-speed zones, road reports, OBD-II, tyre pressure",
    platform: "Android 8+ car head units",
  },
  kavach: {
    free: "Caller card, spam blocking, SMS scam check (with ads, never on call screens)",
    pro: "₹149: no ads",
    platform: "Android phones",
  },
  naukri: {
    free: "Everything: alerts, eligibility match, reminders, official links",
    pro: "None. Free for job seekers",
    platform: "Android and web",
  },
};

/** One table with every app's status and price, kept in step with products in lib/site. */
export function PricingTable() {
  return (
    <section id="pricing" className="scroll-mt-24 py-24">
      <div className="container">
        <SectionHeading
          eyebrow="Status and pricing"
          title="Where each app stands"
          sub={`Updated ${statusUpdated}. Every upgrade is a one-time payment, never a subscription.`}
        />
        <div className="overflow-x-auto rounded-3xl border border-line bg-raised">
          <table className="w-full min-w-[760px] border-collapse text-left text-sm">
            <thead>
              <tr className="text-xs uppercase tracking-wider text-ink-faint">
                <th className="border-b border-line px-5 py-4 font-semibold">App</th>
                <th className="border-b border-line px-5 py-4 font-semibold">Status</th>
                <th className="border-b border-line px-5 py-4 font-semibold">Free</th>
                <th className="border-b border-line px-5 py-4 font-semibold">Pro (one-time)</th>
                <th className="border-b border-line px-5 py-4 font-semibold">Runs on</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className="align-top [&:last-child>td]:border-b-0">
                  <td className="whitespace-nowrap border-b border-line px-5 py-4 font-bold">
                    <span className="mr-2 inline-block size-2.5 rounded-[3px] align-middle" style={{ background: p.accent }} />
                    {p.short}
                  </td>
                  <td className="border-b border-line px-5 py-4">
                    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-3 py-1 text-xs font-semibold ${tone[p.statusTone]}`}>
                      <span className="size-1.5 rounded-full bg-current" /> {p.status}
                    </span>
                  </td>
                  <td className="border-b border-line px-5 py-4 text-ink-muted">{rows[p.id]?.free}</td>
                  <td className="border-b border-line px-5 py-4 text-ink-muted">{rows[p.id]?.pro}</td>
                  <td className="border-b border-line px-5 py-4 text-ink-muted">{rows[p.id]?.platform}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-4 text-center text-xs text-ink-faint">Car Hub maps, stations and cameras © OpenStreetMap contributors (ODbL). Weather: MET Norway.</p>
      </div>
    </section>
  );
}
