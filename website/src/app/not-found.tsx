import Link from "next/link";

export default function NotFound() {
  return (
    <section className="container py-32 text-center">
      <h1 className="text-4xl font-bold">Page not found</h1>
      <p className="mt-3 text-ink-muted">This page skipped a beat.</p>
      <Link href="/" className="mt-8 inline-flex rounded-full bg-brand px-6 py-3 font-semibold text-white">
        Back home
      </Link>
    </section>
  );
}
