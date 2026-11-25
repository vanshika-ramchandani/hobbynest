import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Search, SlidersHorizontal } from "lucide-react";
import { api } from "../api";
import EquipmentCard from "../components/EquipmentCard";
import { Alert, PageHeader, Spinner } from "../components/ui";

export default function Browse() {
  const [params, setParams] = useSearchParams();
  const [cats, setCats] = useState([]);
  const [items, setItems] = useState(null);
  const [error, setError] = useState("");
  const [q, setQ] = useState(params.get("q") || "");

  useEffect(() => {
    api("/categories/", { auth: false }).then(setCats).catch(() => {});
  }, []);

  useEffect(() => {
    setItems(null);
    setError("");
    api(`/equipment/?${params.toString()}`, { auth: false })
      .then(setItems)
      .catch((e) => setError(e.message));
  }, [params]);

  const set = (key, value) => {
    const p = new URLSearchParams(params);
    if (value) p.set(key, value);
    else p.delete(key);
    setParams(p);
  };

  const active = params.get("category") || "";

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <PageHeader title="Browse equipment" subtitle="Everything available to rent in Ahmedabad right now." />

      <div className="card mb-6 flex flex-col gap-3 p-3 lg:flex-row lg:items-center">
        <form
          className="flex flex-1 items-center gap-2"
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            set("q", q.trim());
          }}
        >
          <Search className="ml-2 size-4 text-navy/40" aria-hidden="true" />
          <label htmlFor="browse-q" className="sr-only">Search</label>
          <input
            id="browse-q"
            className="min-w-0 flex-1 bg-transparent py-2 focus:outline-none"
            placeholder="Search guitars, DSLR, badminton..."
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <button className="btn-dark py-2 text-sm">Search</button>
        </form>
        <div className="flex flex-wrap items-center gap-2">
          <SlidersHorizontal className="size-4 text-navy/40" aria-hidden="true" />
          <label className="sr-only" htmlFor="max">Max price per day</label>
          <select id="max" className="input w-auto py-2 text-sm" value={params.get("max_daily") || ""} onChange={(e) => set("max_daily", e.target.value)}>
            <option value="">Any price</option>
            <option value="100">Up to ₹100/day</option>
            <option value="250">Up to ₹250/day</option>
            <option value="500">Up to ₹500/day</option>
          </select>
          <label className="sr-only" htmlFor="sort">Sort</label>
          <select id="sort" className="input w-auto py-2 text-sm" value={params.get("sort") || ""} onChange={(e) => set("sort", e.target.value)}>
            <option value="">Newest</option>
            <option value="price">Price: low to high</option>
            <option value="-price">Price: high to low</option>
          </select>
          <label className="flex cursor-pointer items-center gap-2 rounded-xl border border-navy/15 px-3 py-2 text-sm font-medium">
            <input type="checkbox" className="accent-terracotta" checked={params.get("rent_to_buy") === "1"} onChange={(e) => set("rent_to_buy", e.target.checked ? "1" : "")} />
            Rent-to-Buy
          </label>
          <label className="flex cursor-pointer items-center gap-2 rounded-xl border border-navy/15 px-3 py-2 text-sm font-medium">
            <input type="checkbox" className="accent-terracotta" checked={params.get("beginner") === "1"} onChange={(e) => set("beginner", e.target.checked ? "1" : "")} />
            Beginner
          </label>
        </div>
      </div>

      <div className="mb-8 flex gap-2 overflow-x-auto pb-2" role="tablist" aria-label="Categories">
        {[{ slug: "", name: "All" }, ...cats].map((c) => (
          <button
            key={c.slug}
            role="tab"
            aria-selected={active === c.slug}
            onClick={() => set("category", c.slug)}
            className={`shrink-0 rounded-full px-4 py-2 text-sm font-semibold transition ${
              active === c.slug ? "bg-navy text-white" : "bg-white text-navy/70 ring-1 ring-navy/10 hover:ring-navy/30"
            }`}
          >
            {c.name}
          </button>
        ))}
      </div>

      <Alert>{error}</Alert>
      {items === null && !error ? (
        <Spinner />
      ) : items?.length === 0 ? (
        <div className="card py-16 text-center">
          <p className="text-lg font-bold">No equipment matches your filters.</p>
          <p className="mt-1 text-navy/60">Try a different search or clear the filters.</p>
          <button className="btn-ghost mt-4" onClick={() => { setQ(""); setParams({}); }}>Clear filters</button>
        </div>
      ) : (
        <>
          <p className="mb-4 text-sm text-navy/50">{items?.length} items</p>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {items?.map((item) => <EquipmentCard key={item.id} item={item} />)}
          </div>
        </>
      )}
    </div>
  );
}
