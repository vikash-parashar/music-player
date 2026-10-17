import { Hero } from "@/components/sections/hero";
import { AppsOverview } from "@/components/sections/apps-overview";
import { Features } from "@/components/sections/features";
import { CarHub } from "@/components/sections/carhub";
import { Naukri } from "@/components/sections/naukri";
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
      <Naukri />
      <SocialProof />
      <Support />
      <FAQ />
    </>
  );
}
