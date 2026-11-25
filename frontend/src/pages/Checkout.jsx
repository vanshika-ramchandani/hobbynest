import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { CheckCircle2, CreditCard, FlaskConical, Lock, Smartphone } from "lucide-react";
import { api, fmtDate, inr } from "../api";
import { Alert, ItemVisual, Spinner } from "../components/ui";
import { PriceBreakdown } from "./Detail";

export default function Checkout() {
  const { bookingId } = useParams();
  const nav = useNavigate();
  const [b, setB] = useState(null);
  const [method, setMethod] = useState("upi");
  const [upi, setUpi] = useState("");
  const [card, setCard] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);

  useEffect(() => {
    api(`/bookings/${bookingId}/`).then(setB).catch((e) => setError(e.message));
  }, [bookingId]);

  const pay = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const res = await api(`/bookings/${bookingId}/pay/`, {
        method: "POST",
        body: method === "upi" ? { method, upi_id: upi } : { method, card_number: card },
      });
      setDone(res);
    } catch (err) {
      setError(err.message);
      api(`/bookings/${bookingId}/`).then(setB).catch(() => {});
    } finally {
      setBusy(false);
    }
  };

  if (!b) return error ? <div className="mx-auto max-w-xl px-4 py-16"><Alert>{error}</Alert></div> : <Spinner />;

  if (done) {
    const bk = done.booking;
    return (
      <div className="mx-auto max-w-xl px-4 py-16 text-center">
        <CheckCircle2 className="mx-auto size-16 text-emerald-600" />
        <h1 className="mt-4 text-3xl font-extrabold">Booking confirmed</h1>
        <p className="mt-2 text-navy/60">
          {bk.equipment_detail.title} will be delivered on {fmtDate(bk.start_date)}.
        </p>
        <div className="card mt-8 p-6 text-left">
          <div className="mb-4 flex justify-between text-sm">
            <span className="text-navy/60">Booking ID</span><strong>#HN{String(bk.id).padStart(5, "0")}</strong>
          </div>
          <div className="mb-4 flex justify-between text-sm">
            <span className="text-navy/60">Payment reference</span><strong>{done.reference}</strong>
          </div>
          <PriceBreakdown q={bk} compact />
        </div>
        <div className="mt-6 flex justify-center gap-3">
          <Link to="/bookings" className="btn-dark">View my rentals</Link>
          <Link to="/browse" className="btn-ghost">Keep browsing</Link>
        </div>
      </div>
    );
  }

  const payable = ["pending_payment", "payment_failed"].includes(b.status);

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-extrabold">Checkout</h1>
      <p className="mt-1 text-navy/60">Review your rental and pay securely.</p>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_380px]">
        <form onSubmit={pay} className="card space-y-5 p-6">
          <div className="flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
            <FlaskConical className="mt-0.5 size-4 shrink-0" />
            <div>
              <strong>Test mode.</strong> No real money moves. Any UPI ID works; use <code className="font-mono">fail@test</code> to simulate
              a declined payment. For cards, any 16 digits work; a number ending in <code className="font-mono">0002</code> is declined.
            </div>
          </div>

          <fieldset>
            <legend className="label">Payment method</legend>
            <div className="grid grid-cols-2 gap-3">
              {[["upi", "UPI", Smartphone], ["card", "Card", CreditCard]].map(([v, l, Icon]) => (
                <label
                  key={v}
                  className={`flex cursor-pointer items-center gap-3 rounded-xl border-2 p-4 font-semibold transition ${
                    method === v ? "border-terracotta bg-terracotta-100/50" : "border-navy/10 hover:border-navy/25"
                  }`}
                >
                  <input type="radio" name="method" value={v} checked={method === v} onChange={() => setMethod(v)} className="accent-terracotta" />
                  <Icon className="size-5 text-slate" /> {l}
                </label>
              ))}
            </div>
          </fieldset>

          {method === "upi" ? (
            <div>
              <label htmlFor="upi" className="label">UPI ID</label>
              <input id="upi" className="input" placeholder="yourname@okaxis" value={upi} onChange={(e) => setUpi(e.target.value)} required />
            </div>
          ) : (
            <div>
              <label htmlFor="card" className="label">Card number</label>
              <input id="card" className="input font-mono" inputMode="numeric" placeholder="4111 1111 1111 1111" value={card} onChange={(e) => setCard(e.target.value)} required />
            </div>
          )}

          {b.status === "payment_failed" && !error && <Alert>Your last payment attempt failed. You can try again.</Alert>}
          {error && <Alert>{error}</Alert>}

          {payable ? (
            <button className="btn-primary w-full py-3 text-base" disabled={busy}>
              <Lock className="size-4" /> {busy ? "Processing..." : `Pay ${inr(b.total_payable)}`}
            </button>
          ) : (
            <Alert kind="success">This booking is already {b.status_label.toLowerCase()}.</Alert>
          )}
          <p className="text-xs text-navy/50">
            By paying you agree to the <Link to="/policies#terms" className="underline">rental terms</Link> and{" "}
            <Link to="/policies#refunds" className="underline">cancellation policy</Link>. You can cancel for a full refund until the item is delivered.
          </p>
        </form>

        <aside className="card h-fit overflow-hidden">
          <ItemVisual item={b.equipment_detail} className="aspect-[16/9] w-full" />
          <div className="space-y-4 p-5">
            <div>
              <h2 className="font-bold">{b.equipment_detail.title}</h2>
              <p className="text-sm text-navy/60">{fmtDate(b.start_date)} to {fmtDate(b.end_date)}</p>
              <p className="mt-1 text-xs text-navy/50">Deliver to: {b.delivery_address}</p>
            </div>
            <PriceBreakdown q={b} />
            <button type="button" className="text-sm font-semibold text-navy/60 underline" onClick={() => nav(-1)}>Change dates</button>
          </div>
        </aside>
      </div>
    </div>
  );
}
