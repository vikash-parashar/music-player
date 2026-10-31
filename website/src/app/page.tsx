import { Hero } from "@/components/sections/hero";
import { Bands } from "@/components/sections/bands";
import { AppShowcase } from "@/components/sections/showcase";
import { Pricing } from "@/components/sections/pricing";
import { Support } from "@/components/sections/support";
import { FAQ } from "@/components/sections/faq";

export default function Home() {
  return (
    <>
      <Hero />
      <Bands />
      <AppShowcase />
      <Pricing />
      <Support />
      <FAQ />
    </>
  );
}
