import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { IndianRupee, Package, CalendarCheck, Repeat, Plus, X } from "lucide-react";
import { api, fmtDate, inr } from "../api";
import { Alert, ItemVisual, PageHeader, StatusBadge } from "../components/ui";

const EMPTY = {
  title: "", category: "", description: "", specs: "", condition: "good",
  daily_rate: "", weekly_rate: "", monthly_rate: "", market_value: "",
  rent_to_buy: false, sale_price: "", beginner_friendly: false,
};

function ListingForm({ cats, onSaved, onClose }) {
  const [f, setF] = useState({ ...EMPTY, category: cats[0]?.id || "" });
  const [error, setError] = useState("");
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    const body = { ...f };
    ["daily_rate", "weekly_rate", "monthly_rate", "market_value", "sale_price"].forEach((k) => {
      body[k] = body[k] === "" ? null : Number(body[k]);
    });
    if (!body.rent_to_buy) body.sale_price = null;
    try {
      await api("/equipment/", { method: "POST", body });
      onSaved();
    } catch (err) {
      setError(err.message);
    }
  };

  const num = (k, label) => (
    <div>
      <label className="label" htmlFor={k}>{label}</label>
      <input id={k} type="number" min="0" className="input" value={f[k]} onChange={set(k)} required />
    </div>
  );

  return (
    <form onSubmit={submit} className="card space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-extrabold">New listing</h2>
        <button type="button" onClick={onClose} aria-label="Close"><X className="size-5" /></button>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div><label className="label" htmlFor="title">Title</label><input id="title" className="input" value={f.title} onChange={set("title")} required maxLength={120} /></div>
        <div>
          <label className="label" htmlFor="cat">Category</label>
          <select id="cat" className="input" value={f.category} onChange={set("category")}>
            {cats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
      </div>
      <div><label className="label" htmlFor="desc">Description</label><textarea id="desc" className="input" value={f.description} onChange={set("description")} required /></div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div><label className="label" htmlFor="specs">Key specs</label><input id="specs" className="input" value={f.specs} onChange={set("specs")} /></div>
        <div>
          <label className="label" htmlFor="cond">Condition</label>
          <select id="cond" className="input" value={f.condition} onChange={set("condition")}>
            <option value="new">Like new</option><option value="good">Good</option><option value="fair">Fair</option>
          </select>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {num("daily_rate", "Per day (₹)")}
        {num("weekly_rate", "Per week (₹)")}
        {num("monthly_rate", "Per month (₹)")}
        {num("market_value", "Item value (₹)")}
      </div>
      <p className="text-xs text-navy/50">Item value sets the refundable deposit: none below ₹3,000, 10% up to ₹15,000, 20% up to ₹50,000, 25% above.</p>
      <div className="flex flex-wrap gap-4">
        <label className="flex items-center gap-2 text-sm font-medium"><input type="checkbox" className="accent-terracotta" checked={f.beginner_friendly} onChange={set("beginner_friendly")} /> Beginner friendly</label>
        <label className="flex items-center gap-2 text-sm font-medium"><input type="checkbox" className="accent-terracotta" checked={f.rent_to_buy} onChange={set("rent_to_buy")} /> Allow Rent-to-Buy</label>
      </div>
      {f.rent_to_buy && <div className="max-w-xs">{num("sale_price", "Sale price (₹)")}</div>}
      {error && <Alert>{error}</Alert>}
      <button className="btn-primary">Publish listing</button>
    </form>
  );
}

export default function OwnerDashboard() {
  const [stats, setStats] = useState(null);
  const [bookings, setBookings] = useState([]);
  const [listings, setListings] = useState([]);
  const [requests, setRequests] = useState([]);
  const [cats, setCats] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");

  const load = () => {
    api("/owner/stats/").then(setStats).catch((e) => setError(e.message));
    api("/bookings/?as=owner").then(setBookings).catch(() => {});
    api("/equipment/?mine=1").then(setListings).catch(() => {});
    api("/purchase-requests/").then(setRequests).catch(() => {});
  };
  useEffect(() => {
    load();
    api("/categories/", { auth: false }).then(setCats).catch(() => {});
  }, []);

  const post = async (path) => {
    setError("");
    try {
      await api(path, { method: "POST" });
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  const toggleListing = async (item) => {
    try {
      await api(`/equipment/${item.id}/`, { method: "PATCH", body: { is_active: !item.is_active } });
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  const pendingReqs = requests.filter((r) => r.status === "pending");

  const STATS = stats && [
    { label: "Active listings", value: stats.listings, icon: Package },
    { label: "Ongoing rentals", value: stats.active, icon: CalendarCheck },
    { label: "Earned (completed)", value: inr(stats.earned), icon: IndianRupee },
    { label: "Buy requests", value: stats.pending_buy_requests, icon: Repeat },
  ];

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      <PageHeader title="Owner dashboard" subtitle="Manage your listings, deliveries and returns.">
        <button className="btn-primary" onClick={() => setShowForm(true)}><Plus className="size-4" /> Add equipment</button>
      </PageHeader>

      <Alert>{error}</Alert>

      {STATS && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {STATS.map((s) => (
            <div key={s.label} className="card p-5">
              <s.icon className="size-5 text-slate" />
              <p className="mt-3 text-2xl font-extrabold">{s.value}</p>
              <p className="text-sm text-navy/60">{s.label}</p>
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <div className="mt-6">
          <ListingForm cats={cats} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); load(); }} />
        </div>
      )}

      {pendingReqs.length > 0 && (
        <section className="mt-10">
          <h2 className="text-xl font-extrabold">Rent-to-Buy requests</h2>
          <div className="mt-4 space-y-3">
            {pendingReqs.map((r) => (
              <div key={r.id} className="card flex flex-wrap items-center justify-between gap-4 p-5">
                <div>
                  <p className="font-bold">{r.equipment_title}</p>
                  <p className="text-sm text-navy/60">
                    {r.buyer_name} offers {inr(r.final_price)} ({inr(r.sale_price)} minus {inr(r.rental_credit)} rental credit).
                    You receive {inr(r.final_price - r.commission)} after the 8% sale commission.
                  </p>
                </div>
                <div className="flex gap-2">
                  <button className="btn-primary py-2 text-sm" onClick={() => post(`/purchase-requests/${r.id}/accept/`)}>Accept</button>
                  <button className="btn-ghost py-2 text-sm" onClick={() => post(`/purchase-requests/${r.id}/decline/`)}>Decline</button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="mt-10">
        <h2 className="text-xl font-extrabold">Incoming bookings</h2>
        {bookings.length === 0 ? (
          <p className="mt-3 text-navy/60">No bookings yet.</p>
        ) : (
          <div className="card mt-4 overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="border-b border-navy/10 text-xs uppercase tracking-wider text-navy/50">
                <tr><th className="p-4">Item</th><th className="p-4">Renter</th><th className="p-4">Dates</th><th className="p-4">Your payout</th><th className="p-4">Status</th><th className="p-4">Action</th></tr>
              </thead>
              <tbody>
                {bookings.map((b) => (
                  <tr key={b.id} className="border-b border-navy/5 last:border-0">
                    <td className="p-4 font-semibold">{b.equipment_detail.title}</td>
                    <td className="p-4">{b.renter_name}</td>
                    <td className="p-4 whitespace-nowrap">{fmtDate(b.start_date)} to {fmtDate(b.end_date)}</td>
                    <td className="p-4 tabular-nums">{inr(b.owner_payout)}</td>
                    <td className="p-4"><StatusBadge status={b.status} label={b.status_label} /></td>
                    <td className="p-4">
                      {b.status === "confirmed" && <button className="btn-dark px-3 py-1.5 text-xs" onClick={() => post(`/bookings/${b.id}/deliver/`)}>Mark delivered</button>}
                      {b.status === "returned" && <button className="btn-primary px-3 py-1.5 text-xs" onClick={() => post(`/bookings/${b.id}/complete/`)}>Confirm condition and refund deposit</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="mt-10">
        <h2 className="text-xl font-extrabold">Your listings</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {listings.map((item) => (
            <div key={item.id} className={`card overflow-hidden ${item.is_active ? "" : "opacity-60"}`}>
              <ItemVisual item={item} className="h-28 w-full" />
              <div className="p-4">
                <Link to={`/equipment/${item.id}`} className="font-bold hover:underline">{item.title}</Link>
                <p className="text-sm text-navy/60">{inr(item.daily_rate)}/day · {item.is_active ? "Live" : "Hidden"}</p>
                <button className="mt-3 text-sm font-semibold text-terracotta-700 hover:underline" onClick={() => toggleListing(item)}>
                  {item.is_active ? "Hide listing" : "Make live"}
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
