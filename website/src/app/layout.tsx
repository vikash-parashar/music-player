import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Navbar } from "@/components/sections/navbar";
import { Footer } from "@/components/sections/footer";
import { site } from "@/lib/site";
import { MotionProvider } from "@/components/motion-provider";

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: { default: `${site.product} — Your music, no monthly fee`, template: `%s — ${site.name}` },
  description: site.description,
  applicationName: site.product,
  icons: { icon: "/favicon.png", apple: "/img/apple-touch-icon.png" },
  openGraph: {
    type: "website",
    url: site.url,
    title: site.product,
    description: site.tagline,
    images: [{ url: "/img/og.png", width: 1200, height: 630, alt: site.product }],
    locale: "en_IN",
  },
  twitter: { card: "summary_large_image", title: site.product, description: site.tagline, images: ["/img/og.png"] },
  alternates: { canonical: "/" },
};

export const viewport: Viewport = { themeColor: "#0F0D14", width: "device-width", initialScale: 1 };

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "MobileApplication",
  name: site.product,
  operatingSystem: "Android",
  applicationCategory: "MusicApplication",
  offers: { "@type": "Offer", price: "0", priceCurrency: "INR" },
  publisher: { "@type": "Organization", name: site.company, url: site.url, email: site.email },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <body>
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-full focus:bg-raised focus:px-4 focus:py-2">
          Skip to content
        </a>
        <MotionProvider>
          <Navbar />
          <main id="main">{children}</main>
          <Footer />
        </MotionProvider>
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      </body>
    </html>
  );
}
