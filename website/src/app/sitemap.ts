import type { MetadataRoute } from "next";
import { site } from "@/lib/site";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: `${site.url}/`, changeFrequency: "weekly", priority: 1 },
    { url: `${site.url}/privacy/`, changeFrequency: "monthly", priority: 0.3 },
    { url: `${site.url}/privacy/carhub/`, changeFrequency: "monthly", priority: 0.3 },
    { url: `${site.url}/privacy/naukri/`, changeFrequency: "monthly", priority: 0.3 },
  ];
}
