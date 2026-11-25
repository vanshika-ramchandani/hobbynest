import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, CalendarX, Info, MapPin, Repeat, ShieldCheck, Sparkles, Truck } from "lucide-react";
import { api, fmtDate, inr, todayISO } from "../api";
import { useAuth } from "../auth";
import { Alert, ItemVisual, Rating, Spinner } from "../components/ui";

const CONDITION = { new: "Like new", good: "Good", fair: "Fair" };

export function PriceBreakdown({ q, compact }) {
  const Row = ({ label, value, muted, strong, note }) => (
    <div className={`flex items-start justify-between gap-4 ${strong ? "text-base font-extrabold" : "text-sm"}`}>
      <span className={muted ? "text-navy/60" : ""}>
        {label}
        {note && <span className="block text-xs font-normal text-navy/50">{note}</span>}
      </span>
      <span className="shrink-0 tabular-nums">{value}</span>
    </div>
  );
  return (
    <div className="space-y-2.5">
      <Row label={`Rent for ${q.days} day${q.days > 1 ? "s" : ""}`} value={inr(q.rental_amount)} note="Best of daily, weekly and monthly rates" />
      {q.discount_amount > 0 && (
        <Row label="Discount" value={`- ${inr(q.discount_amount)}`} note={q.discount_label} />
      )}
      <Row label="Delivery and pickup" value={inr(q.delivery_fee)} muted />
      <Row
        label="Refundable deposit"
        value={inr(q.deposit)}
        muted
        note={q.deposit_label || (q.deposit ? "Returned after inspection" : "No deposit needed")}
      />
      <div className="border-t border-dashed border-navy/15 pt-2.5">
        <Row label="Total payable now" value={inr(q.total_payable)} strong />
      </div>
      {!compact && q.deposit > 0 && (
        <p className="text-xs text-navy/50">
          You will get {inr(q.deposit)} back once the owner confirms the item is returned in good condition.
        </p>
      )}
    </div>
  );
}

export default function Detail() {
  const { id } = useParams();
  const { user } = useAuth();
  const nav = useNavigate();
  const [item, setItem] = useState(null);
  const [reviews, setReviews] = useState([]);
  const [booked, setBooked] = useState([]);
  const [start, setStart] = useState(todayISO(1));
  const [end, setEnd] = useState(todayISO(7));
  const [address, setAddress] = useState("");
  const [quote, setQuote] = useState(null);
  const [qErr, setQErr] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api(`/equipment/${id}/`, { auth: false }).then(setItem).catch((e) => setError(e.message));
    api(`/equipment/${id}/reviews/`, { auth: false }).then(setReviews).catch(() => {});
    api(`/equipment/${id}/booked/`, { auth: false }).then(setBooked).catch(() => {});
  }, [id]);

  useEffect(() => {
    if (!start || !end) return;
    setQErr("");
    const t = setTimeout(() => {
      api(`/equipment/${id}/quote/?start=${start}&end=${end}`)
        .then(setQuote)
        .catch((e) => {
          setQuote(null);
          setQErr(e.message);
        });
    }, 200);
    return () => clearTimeout(t);
  }, [id, start, end, user]);

  const book = async (e) => {
    e.preventDefault();
    if (!user) return nav(`/login?next=/equipment/${id}`);
    setBusy(true);
    setError("");
    try {
      const b = await api("/bookings/", {
        method: "POST",
        body: { equipment: Number(id), start_date: start, end_date: end, delivery_address: address },
      });
      nav(`/checkout/${b.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (error && !item) return <div className="mx-auto max-w-3xl px-4 py-16"><Alert>{error}</Alert></div>;
  if (!item) return <Spinner />;

  const isOwn = user && user.id === item.owner;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <Link to="/browse" className="mb-6 inline-flex items-center gap-1 text-sm font-semibold text-navy/60 hover:text-navy">
        <ArrowLeft className="size-4" /> Back to browse
      </Link>

      <div className="grid gap-8 lg:grid-cols-[1fr_420px]">
        <div>
          <ItemVisual item={item} className="aspect-[16/10] w-full rounded-3xl" />

          <div className="mt-6 flex flex-wrap items-center gap-2">
            <span className="chip bg-slate-100 text-slate">{item.category_name}</span>
            <span className="chip bg-navy/5">Condition: {CONDITION[item.condition]}</span>
            {item.beginner_friendly && <span className="chip bg-navy/5"><Sparkles className="size-3" /> Beginner friendly</span>}
            {item.rent_to_buy && <span className="chip bg-terracotta-100 text-terracotta-700"><Repeat className="size-3" /> Rent-to-Buy</span>}
          </div>
          <h1 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">{item.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-4 text-sm text-navy/60">
            <span>Listed by <strong className="text-navy">{item.owner_name}</strong></span>
            <span className="inline-flex items-center gap-1"><MapPin className="size-4" /> {item.location}</span>
            <Rating value={item.rating} count={item.review_count} />
          </div>

          <p className="mt-6 leading-relaxed text-navy/80">{item.description}</p>
          {item.specs && (
            <p className="mt-3 text-sm"><strong>Specs:</strong> <span className="text-navy/70">{item.specs}</span></p>
          )}

          <div className="mt-6 grid grid-cols-3 gap-3">
            {[["Day", item.daily_rate], ["Week", item.weekly_rate], ["Month", item.monthly_rate]].map(([l, v]) => (
              <div key={l} className="card p-4 text-center">
                <p className="text-xs font-semibold uppercase tracking-wider text-navy/50">Per {l}</p>
                <p className="mt-1 text-xl font-extrabold">{inr(v)}</p>
              </div>
            ))}
          </div>

          {item.rent_to_buy && (
            <div className="mt-6 rounded-2xl border border-terracotta/30 bg-terracotta-100/60 p-5">
              <h2 className="flex items-center gap-2 font-bold"><Repeat className="size-5 text-terracotta-700" /> Try before you buy</h2>
              <p className="mt-1 text-sm text-navy/75">
                The owner will sell this exact item for <strong>{inr(item.sale_price)}</strong>. If you rent it first,
                50% of the rent you paid is credited toward the price (up to half the sale price).
              </p>
            </div>
          )}

          <div className="mt-6 grid gap-3 sm:grid-cols-2">
            <div className="flex gap-3 rounded-2xl bg-white p-4 ring-1 ring-navy/8">
              <ShieldCheck className="size-5 shrink-0 text-slate" />
              <div className="text-sm"><strong>Deposit: {item.deposit.amount ? inr(item.deposit.amount) : "None"}</strong><p className="text-navy/60">{item.deposit.label}</p></div>
            </div>
            <div className="flex gap-3 rounded-2xl bg-white p-4 ring-1 ring-navy/8">
              <Truck className="size-5 shrink-0 text-slate" />
              <div className="text-sm"><strong>Doorstep delivery</strong><p className="text-navy/60">Delivery and return pickup by our logistics partner.</p></div>
            </div>
          </div>

          <section className="mt-10">
            <h2 className="text-xl font-extrabold">Reviews from verified renters</h2>
            <p className="text-sm text-navy/50">Only people who completed a rental of this item can review it.</p>
            {reviews.length === 0 ? (
              <p className="mt-4 text-navy/60">No reviews yet.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {reviews.map((r) => (
                  <li key={r.id} className="card p-4">
                    <div className="flex items-center justify-between">
                      <strong>{r.author_name}</strong>
                      <span className="text-terracotta-700" aria-label={`${r.rating} out of 5`}>{"★".repeat(r.rating)}{"☆".repeat(5 - r.rating)}</span>
                    </div>
                    {r.comment && <p className="mt-1 text-sm text-navy/70">{r.comment}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        {/* Booking panel */}
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <form onSubmit={book} className="card space-y-4 p-6">
            <h2 className="text-lg font-extrabold">Book this item</h2>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="start">Start date</label>
                <input id="start" type="date" className="input" min={todayISO(0)} value={start} onChange={(e) => setStart(e.target.value)} required />
              </div>
              <div>
                <label className="label" htmlFor="end">End date</label>
                <input id="end" type="date" className="input" min={start} value={end} onChange={(e) => setEnd(e.target.value)} required />
              </div>
            </div>

            {booked.length > 0 && (
              <div className="rounded-xl bg-sand p-3 text-xs text-navy/70">
                <p className="mb-1 flex items-center gap-1 font-semibold"><CalendarX className="size-3.5" /> Already booked</p>
                {booked.map((b, i) => <p key={i}>{fmtDate(b.start_date)} to {fmtDate(b.end_date)}</p>)}
              </div>
            )}

            {qErr && <Alert>{qErr}</Alert>}
            {quote && (
              <>
                {!quote.available && <Alert>Not available for these dates. Please choose different dates.</Alert>}
                <PriceBreakdown q={quote} />
              </>
            )}

            {user && !user.is_student_verified && (
              <p className="flex gap-2 rounded-xl bg-slate-100 p-3 text-xs text-slate">
                <Info className="size-4 shrink-0" />
                <span>Students get 5% off. <Link to="/profile" className="font-bold underline">Verify your college email</Link>.</span>
              </p>
            )}

            <div>
              <label className="label" htmlFor="addr">Delivery address</label>
              <textarea id="addr" className="input min-h-20" placeholder="Flat, street, area, Ahmedabad" value={address} onChange={(e) => setAddress(e.target.value)} required minLength={10} />
            </div>

            {error && <Alert>{error}</Alert>}
            {isOwn ? (
              <p className="text-center text-sm text-navy/60">This is your listing.</p>
            ) : (
              <button className="btn-primary w-full py-3" disabled={busy || !quote?.available}>
                {busy ? "Reserving..." : user ? "Continue to payment" : "Log in to book"}
              </button>
            )}
            <p className="text-center text-xs text-navy/50">You will review everything before paying. Free cancellation before delivery.</p>
          </form>
        </aside>
      </div>
    </div>
  );
}
