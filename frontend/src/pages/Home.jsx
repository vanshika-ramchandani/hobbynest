import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, BadgeCheck, CalendarRange, Repeat, Search, ShieldCheck, Truck, Wallet, Award, GraduationCap } from "lucide-react";
import { api } from "../api";
import EquipmentCard from "../components/EquipmentCard";
import { CATEGORY_STYLE, CategoryIcon } from "../components/ui";

const STEPS = [
  { icon: Search, title: "Find your gear", text: "Browse guitars, cameras, rackets and more from verified owners and studios in Ahmedabad." },
  { icon: CalendarRange, title: "Pick your dates", text: "Rent by the day, week or month. We automatically apply the cheapest plan for your dates." },
  { icon: Truck, title: "Get it delivered", text: "A delivery partner brings it to your door and picks it up when you are done." },
  { icon: Repeat, title: "Love it? Keep it", text: "On Rent-to-Buy items, half of what you paid in rent counts toward buying that exact item." },
];

const TRUST = [
  { icon: Wallet, title: "Every fee upfront", text: "Rent, delivery and refundable deposit shown before you pay. No hidden charges." },
  { icon: ShieldCheck, title: "Deposits only when needed", text: "Low-value gear needs no deposit. Deposits are refunded after the owner checks the item." },
  { icon: GraduationCap, title: "Student pricing", text: "Verify with your college email for 5% off every rental." },
  { icon: Award, title: "Rewards for good renters", text: "Return on time and in good shape to unlock Bronze, Silver and Gold discounts." },
];

export default function Home() {
  const [cats, setCats] = useState([]);
  const [featured, setFeatured] = useState([]);
  const [q, setQ] = useState("");
  const nav = useNavigate();

  useEffect(() => {
    api("/categories/", { auth: false }).then(setCats).catch(() => {});
    api("/equipment/?beginner=1", { auth: false }).then((d) => setFeatured(d.slice(0, 4))).catch(() => {});
  }, []);

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-navy text-white">
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-slate/40 blur-3xl" />
        <div className="absolute -bottom-32 left-1/3 size-80 rounded-full bg-terracotta/20 blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 py-16 sm:px-6 md:py-24 lg:grid-cols-2">
          <div>
            <span className="chip bg-white/10 text-terracotta-300">
              <BadgeCheck className="size-3.5" /> Now piloting in Ahmedabad
            </span>
            <h1 className="mt-5 text-4xl font-extrabold leading-[1.1] tracking-tight sm:text-5xl lg:text-6xl">
              Try a new hobby <br className="hidden sm:block" />
              <span className="text-terracotta">without buying the gear.</span>
            </h1>
            <p className="mt-5 max-w-lg text-lg text-white/75">
              Rent guitars, cameras, sports kits and creative tools from people nearby. Use it for a week.
              If you love it, buy that same item.
            </p>
            <form
              className="mt-8 flex max-w-lg gap-2 rounded-2xl bg-white p-2"
              onSubmit={(e) => {
                e.preventDefault();
                nav(`/browse?q=${encodeURIComponent(q)}`);
              }}
              role="search"
            >
              <label htmlFor="hero-search" className="sr-only">Search equipment</label>
              <input
                id="hero-search"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Try 'guitar' or 'camera'"
                className="min-w-0 flex-1 rounded-xl px-3 text-navy placeholder:text-navy/40 focus:outline-none"
              />
              <button className="btn-primary">
                <Search className="size-4" /> Search
              </button>
            </form>
            <p className="mt-4 text-sm text-white/50">Rent it. Try it. Love it.</p>
          </div>

          <div className="hidden lg:block">
            <div className="grid grid-cols-2 gap-4">
              {Object.entries(CATEGORY_STYLE).slice(0, 4).map(([slug, s], i) => (
                <div
                  key={slug}
                  className={`flex aspect-square items-center justify-center rounded-3xl shadow-2xl ${i % 2 ? "translate-y-8" : ""}`}
                  style={{ background: `linear-gradient(135deg, ${s.from}, ${s.to})` }}
                >
                  <CategoryIcon slug={slug} className="size-20 text-white/90" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Categories */}
      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <h2 className="text-2xl font-extrabold sm:text-3xl">Explore by hobby</h2>
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {cats.map((c) => (
            <Link
              key={c.slug}
              to={`/browse?category=${c.slug}`}
              className="card group flex flex-col gap-3 p-5 transition hover:-translate-y-0.5 hover:border-terracotta/40"
            >
              <span
                className="flex size-11 items-center justify-center rounded-xl text-white"
                style={{ background: CATEGORY_STYLE[c.slug]?.from || "#3D5A80" }}
              >
                <CategoryIcon slug={c.slug} />
              </span>
              <div>
                <h3 className="font-bold">{c.name}</h3>
                <p className="text-sm text-navy/50">{c.item_count} items</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how" className="scroll-mt-20 bg-sand py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6">
          <h2 className="text-2xl font-extrabold sm:text-3xl">How HobbyNest works</h2>
          <p className="mt-2 max-w-2xl text-navy/60">Four steps from curious to hooked.</p>
          <ol className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((s, i) => (
              <li key={s.title} className="card relative p-6">
                <span className="absolute right-5 top-4 text-5xl font-extrabold text-navy/5">{i + 1}</span>
                <span className="flex size-11 items-center justify-center rounded-xl bg-slate text-white">
                  <s.icon className="size-5" />
                </span>
                <h3 className="mt-4 font-bold">{s.title}</h3>
                <p className="mt-1.5 text-sm text-navy/60">{s.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Featured */}
      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <div className="flex items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl font-extrabold sm:text-3xl">Great for beginners</h2>
            <p className="mt-2 text-navy/60">Easy-to-learn gear at student-friendly prices.</p>
          </div>
          <Link to="/browse" className="hidden items-center gap-1 font-semibold text-terracotta-700 hover:underline sm:flex">
            See all <ArrowRight className="size-4" />
          </Link>
        </div>
        <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {featured.map((item) => (
            <EquipmentCard key={item.id} item={item} />
          ))}
        </div>
      </section>

      {/* Trust */}
      <section className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="rounded-3xl bg-navy p-8 text-white sm:p-12">
          <h2 className="text-2xl font-extrabold sm:text-3xl">Renting you can trust</h2>
          <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {TRUST.map((t) => (
              <div key={t.title}>
                <t.icon className="size-7 text-terracotta" />
                <h3 className="mt-3 font-bold">{t.title}</h3>
                <p className="mt-1 text-sm text-white/65">{t.text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Owner CTA */}
      <section className="mx-auto max-w-7xl px-4 pt-16 sm:px-6">
        <div className="card flex flex-col items-start justify-between gap-6 p-8 sm:flex-row sm:items-center">
          <div>
            <h2 className="text-2xl font-extrabold">Got gear gathering dust?</h2>
            <p className="mt-1 text-navy/60">
              Studios, academies and individuals earn 85% of every rental. We handle payments and delivery.
            </p>
          </div>
          <Link to="/register?role=owner" className="btn-dark shrink-0">
            List your equipment <ArrowRight className="size-4" />
          </Link>
        </div>
      </section>
    </>
  );
}
