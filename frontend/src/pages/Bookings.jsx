import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Repeat, Star } from "lucide-react";
import { api, fmtDate, inr } from "../api";
import { useAuth } from "../auth";
import { Alert, ItemVisual, PageHeader, Spinner, StatusBadge } from "../components/ui";

function ReviewForm({ booking, onDone }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [error, setError] = useState("");
  const submit = async (e) => {
    e.preventDefault();
    try {
      await api(`/bookings/${booking.id}/review/`, { method: "POST", body: { rating, comment } });
      onDone();
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <form onSubmit={submit} className="mt-4 space-y-3 rounded-xl bg-sand p-4">
      <fieldset className="flex items-center gap-1">
        <legend className="mb-1 text-sm font-semibold">Rate this rental</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <button type="button" key={n} onClick={() => setRating(n)} aria-label={`${n} star${n > 1 ? "s" : ""}`} aria-pressed={rating === n}>
            <Star className={`size-6 ${n <= rating ? "fill-terracotta text-terracotta" : "text-navy/25"}`} />
          </button>
        ))}
      </fieldset>
      <label htmlFor={`c${booking.id}`} className="sr-only">Comment</label>
      <textarea id={`c${booking.id}`} className="input" placeholder="How was the equipment and the experience?" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} />
      {error && <Alert>{error}</Alert>}
      <button className="btn-dark py-2 text-sm">Submit review</button>
    </form>
  );
}

function BookingCard({ b, reload, userId }) {
  const [error, setError] = useState("");
  const [showReview, setShowReview] = useState(false);
  const eq = b.equipment_detail;
  const isRenter = eq.owner !== userId;

  const act = async (path, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return;
    setError("");
    try {
      await api(`/bookings/${b.id}/${path}/`, { method: "POST" });
      reload();
    } catch (err) {
      setError(err.message);
    }
  };

  const canBuy = isRenter && eq.rent_to_buy && ["active", "returned", "completed"].includes(b.status) && !b.purchase_request;

  return (
    <article className="card overflow-hidden sm:flex">
      <Link to={`/equipment/${eq.id}`} className="sm:w-48 sm:shrink-0">
        <ItemVisual item={eq} className="h-36 w-full sm:h-full" />
      </Link>
      <div className="flex-1 p-5">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <Link to={`/equipment/${eq.id}`} className="font-bold hover:underline">{eq.title}</Link>
            <p className="text-sm text-navy/60">
              #HN{String(b.id).padStart(5, "0")} · {fmtDate(b.start_date)} to {fmtDate(b.end_date)} · {b.days} days
            </p>
          </div>
          <StatusBadge status={b.status} label={b.status_label} />
        </div>

        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm">
          <span>Paid: <strong>{inr(b.total_payable)}</strong></span>
          {b.deposit > 0 && (
            <span>Deposit {inr(b.deposit)}: <strong className={b.deposit_refunded ? "text-emerald-700" : ""}>{b.deposit_refunded ? "refunded" : "held"}</strong></span>
          )}
          {b.discount_amount > 0 && <span className="text-emerald-700">Saved {inr(b.discount_amount)}</span>}
        </div>

        {b.purchase_request && (
          <p className="mt-3 rounded-lg bg-terracotta-100 px-3 py-2 text-sm text-terracotta-700">
            Purchase request: {inr(b.purchase_request.final_price)} after {inr(b.purchase_request.rental_credit)} rental credit.
            Status: <strong>{b.purchase_request.status}</strong>
          </p>
        )}

        {error && <div className="mt-3"><Alert>{error}</Alert></div>}

        <div className="mt-4 flex flex-wrap gap-2">
          {["pending_payment", "payment_failed"].includes(b.status) && (
            <Link to={`/checkout/${b.id}`} className="btn-primary py-2 text-sm">Complete payment</Link>
          )}
          {["pending_payment", "payment_failed", "confirmed"].includes(b.status) && (
            <button className="btn-ghost py-2 text-sm" onClick={() => act("cancel", "Cancel this booking? Any payment will be fully refunded.")}>Cancel booking</button>
          )}
          {b.status === "active" && (
            <button className="btn-dark py-2 text-sm" onClick={() => act("return")}>Schedule return pickup</button>
          )}
          {b.status === "completed" && !b.review && !showReview && (
            <button className="btn-ghost py-2 text-sm" onClick={() => setShowReview(true)}><Star className="size-4" /> Write a review</button>
          )}
          {canBuy && (
            <button className="btn-ghost py-2 text-sm" onClick={() => act("buy", `Request to buy for ${inr(eq.sale_price)} minus your rental credit?`)}>
              <Repeat className="size-4" /> Buy this item
            </button>
          )}
        </div>
        {b.status === "completed" && b.review && <p className="mt-3 text-sm text-navy/60">You rated this {b.review.rating}/5.</p>}
        {showReview && <ReviewForm booking={b} onDone={reload} />}
      </div>
    </article>
  );
}

export default function Bookings() {
  const { user, refreshUser } = useAuth();
  const [list, setList] = useState(null);
  const [error, setError] = useState("");

  const load = () => {
    api("/bookings/")
      .then((all) => setList(all.filter((b) => b.equipment_detail.owner !== user.id)))
      .catch((e) => setError(e.message));
    refreshUser();
  };

  useEffect(load, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <PageHeader title="My rentals" subtitle="Track deliveries, returns, deposits and reviews." />
      <Alert>{error}</Alert>
      {list === null ? (
        <Spinner />
      ) : list.length === 0 ? (
        <div className="card py-16 text-center">
          <p className="text-lg font-bold">No rentals yet</p>
          <p className="mt-1 text-navy/60">Find something you have always wanted to try.</p>
          <Link to="/browse" className="btn-primary mt-4">Browse equipment</Link>
        </div>
      ) : (
        <div className="space-y-4">
          {list.map((b) => <BookingCard key={b.id} b={b} reload={load} userId={user.id} />)}
        </div>
      )}
    </div>
  );
}
