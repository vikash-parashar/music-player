import { Hero } from "@/components/sections/hero";
import { AppsOverview } from "@/components/sections/apps-overview";
import { Features } from "@/components/sections/features";
import { CarHub } from "@/components/sections/carhub";
import { Kavach } from "@/components/sections/kavach";
import { Naukri } from "@/components/sections/naukri";
import { PricingTable } from "@/components/sections/pricing-table";
import { SocialProof } from "@/components/sections/social-proof";
import { Support } from "@/components/sections/support";
import { FAQ } from "@/components/sections/faq";

export default function Home() {
  return (
    <>
      <Hero />
      <AppsOverview />
      <Features />
      <CarHub />
      <Kavach />
      <Naukri />
      <PricingTable />
      <SocialProof />
      <Support />
      <FAQ />
    </>
  );
}
