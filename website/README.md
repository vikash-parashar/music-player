# ampliapps.in landing page

Next.js 14 (app router) + Tailwind CSS + Framer Motion, exported as a static site to GitHub Pages.

```bash
cd website
npm ci
npm run dev     # http://localhost:3000
npm run build   # static site in out/
```

- Sections: `src/components/sections/` (navbar, hero, features, social proof, pricing, FAQ, footer).
- Animated UI pieces: `src/components/ui/`, adapted from Magic UI (MIT), as listed on 21st.dev.
- Brand tokens: `tailwind.config.ts` (night, raised, line, ink, rose, slate, `bg-brand`).
- Copy, links, FAQ: `src/lib/site.ts`. The privacy page is generated from `../PRIVACY.md`:
  `pandoc ../PRIVACY.md -t html -o src/content/privacy.html`.
- `public/` holds CNAME, app-ads.txt and icons, copied into the build.
- Deploy: `.github/workflows/deploy-site.yml` deploys on every push to main that touches `website/`.
  Pages Source must be "GitHub Actions".
