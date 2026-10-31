export const site = {
  name: "Ampli",
  url: "https://ampliapps.in",
  email: "support@ampliapps.in",
  tagline: "Apps made in India, for every Indian. Pay once or use free, never a monthly fee.",
  description:
    "Ampli makes Android apps for India: Ampli MP3 Player, Ampli Car Hub (the car that talks in Haryanvi) and Ampli Naukri (free government job alerts). Made in India.",
  company: "Ampli Studio",
};

/** Not on Google Play yet: buttons ask people to get notified by email until each app is live. */
export const notifyHref = (app: string) =>
  `mailto:${site.email}?subject=${encodeURIComponent(`Notify me: ${app} on Google Play`)}&body=${encodeURIComponent(
    `Please email me when ${app} launches on Google Play.`,
  )}`;

export const feedbackHref = `mailto:${site.email}?subject=${encodeURIComponent("Feedback for Ampli")}`;

export const shareText =
  "Ampli: desi apps from India. A music player with no monthly fee, a car system that talks in Haryanvi, and free govt job alerts. https://ampliapps.in";

export const nav = [
  { href: "/#apps", label: "Apps" },
  { href: "/#music", label: "MP3 Player" },
  { href: "/#carhub", label: "Car Hub" },
  { href: "/#naukri", label: "Naukri" },
  { href: "/#support", label: "Support us" },
  { href: "/#faq", label: "FAQ" },
];

export const footerLinks = [
  ...nav,
  { href: "/privacy/", label: "Privacy (MP3)" },
  { href: "/privacy/carhub/", label: "Privacy (Car Hub)" },
  { href: "/privacy/naukri/", label: "Privacy (Naukri)" },
  { href: "/privacy/kavach/", label: "Privacy (Kavach)" },
];

export type Product = {
  id: string;
  name: string;
  short: string;
  tagline: string;
  icon: "mp3" | "carhub" | "naukri";
  price: string;
  accent: string;
  accentSoft: string;
  points: string[];
};

export const products: Product[] = [
  {
    id: "music",
    name: "Ampli MP3 Player",
    short: "MP3 Player",
    tagline: "Your music, no monthly fee. Pay once, play forever.",
    icon: "mp3",
    price: "Free · Pro ₹199 once",
    accent: "#E11D48",
    accentSoft: "#FDA4AF",
    points: ["Offline player for your own songs", "Record-player look, equalizer, lyrics", "6 Indian languages"],
  },
  {
    id: "carhub",
    name: "Ampli Car Hub",
    short: "Car Hub",
    tagline: "The world's first car system that talks in Haryanvi.",
    icon: "carhub",
    price: "Free · Pro ₹1,499 once (launch price)",
    accent: "#0891B2",
    accentSoft: "#67E8F9",
    points: ["Human Haryanvi voices, fully offline", "“Hey Ampli” voice control", "SOS, FASTag, speed cameras"],
  },
  {
    id: "naukri",
    name: "Ampli Naukri",
    short: "Naukri",
    tagline: "Free sarkari job alerts that match your eligibility.",
    icon: "naukri",
    price: "Free for job seekers",
    accent: "#EA580C",
    accentSoft: "#FDBA74",
    points: ["Jobs matched to your age and marks", "Admit card, exam and result alerts", "Hindi first, official links only"],
  },
];

export const faqs = [
  {
    q: "Are Ampli apps on Google Play?",
    a: "Not yet. Google is reviewing our developer account. Tap “Notify me” on any app and we will email you the day it goes live.",
  },
  {
    q: "Is there a monthly fee for any Ampli app?",
    a: "No. Every app is free to use. The MP3 Player and Car Hub have an optional Pro upgrade that you pay for once and keep for life. Ampli Naukri is free for job seekers.",
  },
  {
    q: "What does Ampli MP3 Player Pro add?",
    a: "No ads, a 10-band equalizer with bass boost and 3D surround, a music visualiser, AMOLED black and wallpaper colours, Chromecast and the video player. ₹199 once.",
  },
  {
    q: "Does Car Hub really speak Haryanvi?",
    a: "Yes. Car Hub greets you, guides turns and warns about speed cameras in real Haryanvi, with two human-sounding voices you can pick from. The voices are inside the app, so they work with no internet. Emergency prompts always stay in plain Hindi.",
  },
  {
    q: "What does Car Hub Pro add?",
    a: "All 25 home-screen themes, live engine data and fault codes from an OBD-II adapter, Bluetooth tyre pressure alerts and CSV export of your car expenses. ₹1,499 once at launch, ₹2,500 after.",
  },
  {
    q: "Is Ampli Naukri free?",
    a: "Yes, for every job seeker. Alerts, eligibility match, reminders and official notice links are all free. We always link to the official notification, and we never ask you for money to apply.",
  },
  {
    q: "Can I download songs from YouTube with Ampli?",
    a: "No. Ampli respects artists and copyright. The MP3 Player plays music you already own, plus legally free audiobooks and podcasts.",
  },
  {
    q: "What does the MP3 Player send over the internet?",
    a: "Your songs never leave your phone. The internet is used for audiobooks and podcasts, ads in the free version, Google Play purchases, song recognition (only when you tap it), and Help & feedback (only when you send a message). The app also sends a random app ID with installs, purchases and simple usage counts, which you can turn off. The privacy page has the full list.",
  },
  {
    q: "How can I help Ampli grow?",
    a: "Download the apps when they launch, use them, rate them honestly on Google Play, send us feedback, and share Ampli with friends and family. If you want, you can also support us with money once the support link is live.",
  },
];
