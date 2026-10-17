/** Static export: the site is plain HTML/CSS/JS served free by GitHub Pages at ampliapps.in. */
const nextConfig = {
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
  poweredByHeader: false,
  reactStrictMode: true,
};
export default nextConfig;
