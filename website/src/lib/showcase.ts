/** Home page showcase: real app screens (public/img/apps) and features for each Ampli app. Keep in step with each app's thread. */
export type Showcase = {
  id: string;
  tint: "mp3" | "car" | "kv" | "nk";
  name: string;
  short: string;
  status: { tone: "live" | "test" | "dev"; label: string };
  price: string;
  landscape?: boolean;
  intro: string;
  shots: { src: string; caption: string }[];
  features: { icon: string; title: string; body: string; pro?: boolean }[];
};

const shot = (src: string, caption: string) => ({ src: `/img/apps/${src}.webp`, caption });

export const showcase: Showcase[] = [
  {
    id: "music",
    tint: "mp3",
    name: "Ampli MP3",
    short: "MP3",
    status: { tone: "test", label: "Testing on Google Play" },
    price: "Free · Pro ₹199 once",
    intro: "An offline music player that feels like vinyl. Your songs stay on your phone, there is no monthly fee, and there is a player look for every mood.",
    shots: [
      shot("mp3-0", "Record player with the song on the label"),
      shot("mp3-1", "Your library, offline, no monthly fee"),
      shot("mp3-2", "Equalizer for every device"),
      shot("mp3-3", "Search in Hinglish or Hindi"),
      shot("mp3-4", "Lyrics, bookmarks and A-B repeat"),
      shot("mp3-5", "Animated progress bars and themes"),
      shot("mp3-6", "Free ringtone maker"),
    ],
    features: [
      { icon: "💿", title: "9 player styles", body: "Record, Car beat lights, cassette and more. Free users keep Record and Car.", pro: true },
      { icon: "🎚️", title: "10-band equalizer", body: "Bass boost and 3D sound, with a separate setting for earphones, speaker and car.", pro: true },
      { icon: "🔎", title: "Hinglish search", body: "Type “tum hi ho” and it finds तुम ही हो, and the other way round." },
      { icon: "📖", title: "Ampli Stories", body: "Hindi audio series such as Vikram aur Betal. The first 5 episodes are free." },
      { icon: "🚗", title: "Car mode", body: "Big buttons, swipe controls, and car lights that blink to the beat." },
      { icon: "✂️", title: "Ringtone maker", body: "Cut any song, add fades, and set it as your ringtone in one tap." },
    ],
  },
  {
    id: "carhub",
    tint: "car",
    name: "Ampli Car Hub",
    short: "Car Hub",
    status: { tone: "test", label: "Testing in real cars" },
    price: "Free · Pro ₹1,499 launch price",
    landscape: true,
    intro: "A voice-first launcher for Android car screens. Hands on the wheel, eyes on the road, and a co-driver that speaks Haryanvi.",
    shots: [
      shot("car-0", "Home screen with speed and quick apps"),
      shot("car-1", "Turn-by-turn navigation in Hindi"),
      shot("car-2", "Trip cost: fuel and tolls"),
      shot("car-4", "SOS message ready in one tap"),
      shot("car-5", "Crash alert countdown"),
      shot("car-6", "FASTag balance and toll history"),
      shot("car-7", "Quick controls"),
      shot("car-3", "More from Ampli"),
    ],
    features: [
      { icon: "🎙️", title: "Hey Ampli", body: "Voice commands for music, maps, calls and weather, in Hindi, Haryanvi and about 20 languages." },
      { icon: "🧭", title: "Navigation", body: "Turn-by-turn directions with speed camera alerts (533 cameras)." },
      { icon: "⛽", title: "CNG & EV finder", body: "19,400 fuel, CNG and EV charging stations, on the map and read aloud." },
      { icon: "🆘", title: "SOS & crash alert", body: "Shares your location and blood group with family when it matters." },
      { icon: "🏷️", title: "FASTag & trip cost", body: "Toll history and the fuel plus toll cost of every trip." },
      { icon: "🔧", title: "OBD-II & tyre pressure", body: "Engine data and tyre pressure warnings from low-cost Bluetooth dongles.", pro: true },
    ],
  },
  {
    id: "kavach",
    tint: "kv",
    name: "Ampli Kavach",
    short: "Kavach",
    status: { tone: "dev", label: "In development" },
    price: "Free · Pro ₹149 once",
    intro: "A scam-call and scam-message shield for Indian families. Hindi first, and it never uploads your contacts.",
    shots: [
      shot("kv-0", "Home: what Kavach stopped this week"),
      shot("kv-1", "Warning card when a scam number calls"),
      shot("kv-2", "Scam Check for any message or link"),
    ],
    features: [
      { icon: "📞", title: "Scam call warning", body: "A big, clear card appears over any dialer before you answer." },
      { icon: "💬", title: "Scam message check", body: "Paste or share any SMS, WhatsApp message or link to get a plain verdict." },
      { icon: "🧾", title: "Loan-app blackmail shield", body: "Free help: block, report, and a ready-made cybercrime complaint." },
      { icon: "🚔", title: "Digital arrest check", body: "Tap what is happening and get a clear answer: hang up and call 1930." },
      { icon: "🔔", title: "WhatsApp & Telegram alerts", body: "Warns you about scam messages in chat apps. Off by default, and it all runs on the phone." },
      { icon: "👨‍👩‍👧", title: "Family link", body: "See when Mummy or Papa gets a risky call. No contact uploads, ever.", pro: true },
    ],
  },
  {
    id: "naukri",
    tint: "nk",
    name: "Ampli Naukri",
    short: "Naukri",
    status: { tone: "live", label: "Built, launching soon" },
    price: "Free",
    intro: "Government job alerts that show only the jobs you can actually apply for, with your age and category relaxation already worked out.",
    shots: [
      shot("nk-0", "Jobs that match you today"),
      shot("nk-1", "Recruitment stages, from form to result"),
      shot("nk-3", "Your profile in one place"),
      shot("nk-4", "Photo and signature resizer"),
      shot("nk-5", "Also on the web"),
    ],
    features: [
      { icon: "✅", title: "Eligibility match", body: "Your age, category and education are checked against every notice." },
      { icon: "⏰", title: "Deadline alerts", body: "Reminders before the last date, admit card and exam day." },
      { icon: "🧭", title: "Stage timeline", body: "Form, admit card, exam, answer key and result, all in one line." },
      { icon: "🖼️", title: "Photo & signature resizer", body: "Sized for SSC and state exams, and it works fully on your phone." },
      { icon: "🇮🇳", title: "Hindi & English", body: "Switch any time. Big, clear text for every reader." },
      { icon: "🎓", title: "Free for students", body: "No Pro needed. Supported by coaching partners and ads." },
    ],
  },
];

export const pricingRows = [
  ["Ampli MP3", "Testing on Google Play", "Full player, Record and Car styles, Stories episodes 1-5", "₹199 once", "Android"],
  ["Ampli Car Hub", "Testing in real cars", "Voice, navigation, CNG/EV finder, SOS", "₹1,499 launch price, then ₹2,500", "Android car screens"],
  ["Ampli Kavach", "In development", "Call warning, message check, loan-app shield", "₹149 once", "Android"],
  ["Ampli Naukri", "Built, launching soon", "Everything", "Not needed", "Android + web"],
];
