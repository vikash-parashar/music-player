import type { Config } from "tailwindcss";

/**
 * Ampli design tokens (same palette as the Android app and brand kit):
 * night background, raised surfaces, slate → rose brand gradient.
 */
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    container: { center: true, padding: { DEFAULT: "1.25rem", lg: "2rem" }, screens: { "2xl": "1120px" } },
    extend: {
      colors: {
        night: { DEFAULT: "#0F0D14", 900: "#0B0A0F" },
        raised: { DEFAULT: "#1C1820", hover: "#241F29" },
        line: "#2A2430",
        ink: { DEFAULT: "#F5F2F7", muted: "#B9B0C0", faint: "#8A8193" },
        rose: { 300: "#FDA4AF", 400: "#FB7185", 500: "#F43F5E", 600: "#E11D48", 700: "#BE123C" },
        slate: { 700: "#334155", 800: "#1E293B" },
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "-apple-system", "Segoe UI", "Roboto", "sans-serif"],
      },
      borderRadius: { xl: "14px", "2xl": "20px", "3xl": "28px" },
      backgroundImage: {
        brand: "linear-gradient(135deg,#334155 0%,#E11D48 100%)",
        "brand-text": "linear-gradient(90deg,#FDA4AF 0%,#E11D48 100%)",
      },
      boxShadow: {
        glow: "0 30px 80px -20px rgba(225,29,72,.45)",
        card: "0 1px 0 rgba(255,255,255,.04) inset, 0 20px 40px -24px rgba(0,0,0,.6)",
      },
      keyframes: {
        marquee: { from: { transform: "translateX(0)" }, to: { transform: "translateX(calc(-100% - var(--gap)))" } },
        "shimmer-slide": { to: { transform: "translate(calc(100cqw - 100%), 0)" } },
        "spin-around": {
          "0%": { transform: "translateZ(0) rotate(0)" },
          "15%, 35%": { transform: "translateZ(0) rotate(90deg)" },
          "65%, 85%": { transform: "translateZ(0) rotate(270deg)" },
          "100%": { transform: "translateZ(0) rotate(360deg)" },
        },
        "eq-bar": { "0%,100%": { transform: "scaleY(.35)" }, "50%": { transform: "scaleY(1)" } },
      },
      animation: {
        marquee: "marquee var(--duration) infinite linear",
        "shimmer-slide": "shimmer-slide var(--speed) ease-in-out infinite alternate",
        "spin-around": "spin-around calc(var(--speed) * 2) infinite linear",
        "eq-bar": "eq-bar 1.1s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};
export default config;
