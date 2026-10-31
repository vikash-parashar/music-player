export const site = {
  name: "Ampli",
  url: "https://ampliapps.in",
  email: "support@ampliapps.in",
  tagline: "Apps made in India, for every Indian. Pay once or use free, never a monthly fee.",
  description:
    "Ampli makes Android apps: Ampli MP3 Player, Ampli Car Hub (the car that talks in Haryanvi), Ampli Kavach (scam-call shield for families) and Ampli Naukri (free government job alerts). Made in India.",
  company: "Ampli Studio",
};

/** Not on Google Play yet: buttons ask people to get notified by email until each app is live. */
export const notifyHref = (app: string) =>
  `mailto:${site.email}?subject=${encodeURIComponent(`Notify me: ${app} on Google Play`)}&body=${encodeURIComponent(
    `Please email me when ${app} launches on Google Play.`,
  )}`;

export const feedbackHref = `mailto:${site.email}?subject=${encodeURIComponent("Feedback for Ampli")}`;

export const shareText =
  "Ampli: desi apps from India. A music player with no monthly fee, a car system that talks in Haryanvi, a scam-call shield for families, and free govt job alerts. https://ampliapps.in";

export const nav = [
  { href: "/#music", label: "MP3" },
  { href: "/#carhub", label: "Car Hub" },
  { href: "/#kavach", label: "Kavach" },
  { href: "/#naukri", label: "Naukri" },
  { href: "/#pricing", label: "Pricing" },
  { href: "/#support", label: "Support" },
  { href: "/#faq", label: "FAQ" },
];

export const footerLinks = [
  ...nav,
  { href: "/privacy/", label: "Privacy (MP3)" },
  { href: "/privacy/carhub/", label: "Privacy (Car Hub)" },
  { href: "/privacy/naukri/", label: "Privacy (Naukri)" },
  { href: "/privacy/kavach/", label: "Privacy (Kavach)" },
];

export type AppId = "mp3" | "carhub" | "kavach" | "naukri";

/** When the status table was last checked; update with every status change. */
export const statusUpdated = "31 October 2026";

export type Product = {
  id: string;
  name: string;
  short: string;
  tagline: string;
  icon: AppId;
  price: string;
  /** Short status label and its tone (test = in testing, dev = in development). */
  status: string;
  statusTone: "live" | "test" | "dev";
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
    status: "Testing on Google Play",
    statusTone: "test",
    accent: "#E11D48",
    accentSoft: "#FDA4AF",
    points: [
      "Offline player for your own songs and videos",
      "10 Now Playing looks, including a car whose lights follow the beat",
      "Equalizer, lyrics, visualiser, internet radio",
      "Car mode, Chromecast, Hi-Res and USB DAC output",
    ],
  },
  {
    id: "carhub",
    name: "Ampli Car Hub",
    short: "Car Hub",
    tagline: "The world's first car system that talks in Haryanvi.",
    icon: "carhub",
    price: "Free · Pro ₹1,499 once (launch price)",
    status: "Testing in real cars",
    statusTone: "test",
    accent: "#0891B2",
    accentSoft: "#67E8F9",
    points: [
      "Talks in Haryanvi, Hindi and about 20 more languages",
      "“Hey Ampli” voice control that works offline",
      "New: CNG, petrol and EV finder, 19,400 stations offline",
      "533 speed camera alerts, SOS help, FASTag, trips",
    ],
  },
  {
    id: "kavach",
    name: "Ampli Kavach",
    short: "Kavach",
    tagline: "Scam-call shield for your family. Your contacts never leave your phone.",
    icon: "kavach",
    price: "Free · Pro ₹149 once",
    status: "In development",
    statusTone: "dev",
    accent: "#0D9488",
    accentSoft: "#5EEAD4",
    points: [
      "Caller card that flags spam and scam calls",
      "Scam-link check for SMS, on the phone only",
      "Loan-app blackmail and “digital arrest” help",
      "One tap to the official cybercrime complaint site",
    ],
  },
  {
    id: "naukri",
    name: "Ampli Naukri",
    short: "Naukri",
    tagline: "Free sarkari job alerts that match your eligibility.",
    icon: "naukri",
    price: "Free for job seekers",
    status: "Built, launching soon",
    statusTone: "test",
    accent: "#EA580C",
    accentSoft: "#FDBA74",
    points: [
      "Jobs matched to your age, marks and state",
      "Admit card, exam and result alerts",
      "Hindi first, official notice links only",
      "Track every form from applied to result",
    ],
  },
];

export const faqs = [
  {
    q: "Are Ampli apps on Google Play?",
    a: "Not publicly yet. Ampli MP3 Player is in closed testing on Google Play, Car Hub is being tested in real cars, Kavach is in development and Naukri launches soon. Tap “Notify me” on any app and we will email you the day it goes live.",
  },
  {
    q: "Is there a monthly fee for any Ampli app?",
    a: "No. Every app is free to use. The MP3 Player, Car Hub and Kavach have an optional Pro upgrade that you pay for once and keep for life. Ampli Naukri is free for job seekers.",
  },
  {
    q: "Does Ampli Kavach upload my contacts?",
    a: "No. Kavach does its work on your phone. It reads contacts only to show names and let their calls through, and never uploads your contact list or your SMS. Online protection is optional and off by default; it sends only the one unknown number being checked.",
  },
  {
    q: "What does Ampli MP3 Player Pro add?",
    a: "No ads, all 10 Now Playing styles and 10 car light shows, a 10-band equalizer with bass boost and 3D surround, network folders, Hi-Res output, Chromecast and the video player. ₹199 once.",
  },
  {
    q: "Does Car Hub really speak Haryanvi?",
    a: "Yes. Car Hub greets you, guides turns and warns about speed cameras in real Haryanvi, with two human-sounding voices you can pick from. The voices are inside the app, so they work with no internet. Emergency prompts always stay in plain Hindi.",
  },
  {
    q: "What does Car Hub Pro add?",
    a: "Red-light cameras, average-speed zones, the “Coming up on this road” list and road reports, plus live engine data from an OBD-II adapter and Bluetooth tyre pressure alerts. ₹1,499 once for the first 60 days after launch, ₹2,500 after.",
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
