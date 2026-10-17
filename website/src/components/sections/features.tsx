"use client";
import { SlidersHorizontal, Car, Scissors, Disc3, Mic2, Wifi, Moon, BookOpen, Languages } from "lucide-react";
import { PhoneMockup } from "@/components/sections/phone-mockup";
import { FeatureGrid, PlanCards, ProductHeading, type Feature, type Plan } from "@/components/sections/product-blocks";
import { BlurFade } from "@/components/ui/blur-fade";

const ACCENT = "#E11D48";
const SOFT = "#FDA4AF";

const features: Feature[] = [
  { icon: Disc3, title: "A record player for every song", body: "Your song's poster becomes a spinning record. The arm moves on when you play and lifts off when you pause." },
  { icon: SlidersHorizontal, title: "Equalizer for every device", body: "A different sound for earphones, Bluetooth speaker and car, switched by itself when you connect." },
  { icon: Car, title: "Car mode", body: "Huge buttons, swipe to skip and voice search in Hindi or English, so your eyes stay on the road." },
  { icon: Scissors, title: "Ringtone maker", body: "Cut any song into a ringtone, alarm or a special tone for one contact, with smooth fades." },
  { icon: Mic2, title: "Lyrics and bookmarks", body: "Read lyrics from your own files, mark your favourite moments, and jump back to them." },
  { icon: Wifi, title: "Wi-Fi transfer", body: "Send songs from your laptop to your phone over Wi-Fi. No cable, no app on the laptop." },
  { icon: Moon, title: "Sleep timer and speed", body: "Stop at the end of a song or after a set time. Change speed and pitch for practice." },
  { icon: BookOpen, title: "Audiobooks and podcasts", body: "Free public-domain audiobooks and podcasts in a separate tab, all legal." },
  { icon: Languages, title: "6 Indian languages", body: "English, Hindi, Tamil, Telugu, Bengali and Marathi, with more on the way." },
];

const plans: Plan[] = [
  {
    name: "Free",
    price: "₹0",
    note: "forever",
    blurb: "Every music feature, with no limits on your own songs.",
    features: ["All offline playback features", "Record-player look, equalizer, lyrics", "Ringtone maker and car mode", "Audiobooks and podcasts", "A small banner ad"],
  },
  {
    name: "Ampli Pro",
    price: "₹199",
    note: "once · lifetime",
    blurb: "Support an Indian indie app and unlock the extras.",
    features: ["Everything in Free, with no ads", "10-band equalizer, bass boost, 3D surround", "Music visualiser", "AMOLED black and wallpaper colours", "Chromecast to your TV and the video player"],
    highlight: true,
  },
];

export function Features() {
  return (
    <section id="music" className="scroll-mt-24 border-t border-line py-24">
      <div className="container">
        <ProductHeading
          app="mp3"
          eyebrow="Ampli MP3 Player"
          title={
            <>
              Your music. <span className="bg-brand-text bg-clip-text text-transparent">No monthly fee.</span>
            </>
          }
          sub="A fast, beautiful offline player for the songs already on your phone. No account, no streaming bill. Pay once, play forever."
          accent={SOFT}
        />
        <div className="grid items-center gap-12 lg:grid-cols-[.8fr_1.2fr]">
          <BlurFade offset={24}>
            <PhoneMockup />
          </BlurFade>
          <FeatureGrid features={features.slice(0, 6)} accent={ACCENT} twoCols />
        </div>
        <div className="mt-4">
          <FeatureGrid features={features.slice(6)} accent={ACCENT} />
        </div>
        <PlanCards plans={plans} accent={ACCENT} accentSoft={SOFT} appName="Ampli MP3 Player" />
      </div>
    </section>
  );
}
