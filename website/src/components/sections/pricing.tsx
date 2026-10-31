import { pricingRows } from "@/lib/showcase";
import { statusUpdated } from "@/lib/site";
import { Reveal } from "@/components/ui/reveal";

export function Pricing() {
  return (
    <section id="pricing" className="wrap section" aria-labelledby="pricing-title">
      <Reveal>
        <p className="eyebrow">Where each app stands · {statusUpdated}</p>
      </Reveal>
      <Reveal>
        <h2 id="pricing-title" className="disp section-title">
          Pay once.
          <br />
          <em>Own it.</em>
        </h2>
      </Reveal>
      <Reveal className="tbl">
        <table>
          <thead>
            <tr>
              <th scope="col">App</th>
              <th scope="col">Status</th>
              <th scope="col">Free</th>
              <th scope="col">Pro</th>
              <th scope="col">Platform</th>
            </tr>
          </thead>
          <tbody>
            {pricingRows.map((r) => (
              <tr key={r[0]}>
                <td>
                  <b>{r[0]}</b>
                </td>
                {r.slice(1).map((c, i) => (
                  <td key={i}>{c}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </Reveal>
      <p className="fine-print">
        Maps from OpenStreetMap contributors. Weather from MET Norway. Prices are one-time Google Play purchases, with no subscriptions (except the optional Ampli Stories Pass).
      </p>
    </section>
  );
}
