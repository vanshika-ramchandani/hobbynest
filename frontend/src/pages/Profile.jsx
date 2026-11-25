import { useState } from "react";
import { Award, GraduationCap, CheckCircle2 } from "lucide-react";
import { api } from "../api";
import { useAuth } from "../auth";
import { Alert, PageHeader } from "../components/ui";

const TIERS = [
  { name: "Bronze", needed: 3, discount: 5, color: "#B07A4B" },
  { name: "Silver", needed: 7, discount: 10, color: "#8A94A6" },
  { name: "Gold", needed: 15, discount: 15, color: "#D4A23A" },
];

export default function Profile() {
  const { user, refreshUser } = useAuth();
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState({});
  const [form, setForm] = useState({ first_name: user.first_name, last_name: user.last_name, phone: user.phone });

  const verify = async (e) => {
    e.preventDefault();
    setMsg({});
    try {
      await api("/auth/verify-student/", { method: "POST", body: { college_email: email } });
      await refreshUser();
      setMsg({ ok: "Student status verified. 5% off is now applied to your rentals." });
    } catch (err) {
      setMsg({ err: err.message });
    }
  };

  const save = async (e) => {
    e.preventDefault();
    setMsg({});
    try {
      await api("/auth/me/", { method: "PATCH", body: form });
      await refreshUser();
      setMsg({ ok: "Profile saved." });
    } catch (err) {
      setMsg({ err: err.message });
    }
  };

  const max = 15;
  const pct = Math.min(100, (user.completed_rentals / max) * 100);

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <PageHeader title={`Hi, ${user.first_name || user.username}`} subtitle={`${user.role === "owner" ? "Equipment owner" : "Renter"} account · ${user.email}`} />

      <div className="space-y-3">
        {msg.ok && <Alert kind="success">{msg.ok}</Alert>}
        {msg.err && <Alert>{msg.err}</Alert>}
      </div>

      <section className="mt-4 overflow-hidden rounded-3xl bg-navy p-6 text-white sm:p-8">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Award className="size-10 text-terracotta" />
            <div>
              <p className="text-sm text-white/60">Your badge</p>
              <p className="text-2xl font-extrabold">{user.reward_tier.name}</p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-sm text-white/60">Reward points</p>
            <p className="text-2xl font-extrabold">{user.reward_points}</p>
          </div>
        </div>

        <div className="mt-8">
          <div className="relative h-3 rounded-full bg-white/15">
            <div className="h-3 rounded-full bg-terracotta transition-all" style={{ width: `${pct}%` }} />
            {TIERS.map((t) => (
              <span key={t.name} className="absolute top-1/2 size-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-navy" style={{ left: `${(t.needed / max) * 100}%`, background: user.completed_rentals >= t.needed ? t.color : "#34466b" }} />
            ))}
          </div>
          <div className="relative mt-3 h-10 text-xs">
            {TIERS.map((t) => (
              <div key={t.name} className={`absolute whitespace-nowrap ${t.needed === max ? "-translate-x-full text-right" : "-translate-x-1/2 text-center"}`} style={{ left: `${(t.needed / max) * 100}%` }}>
                <p className="font-bold">{t.name}</p>
                <p className="text-white/60">{t.discount}% off</p>
              </div>
            ))}
          </div>
        </div>
        <p className="mt-4 text-sm text-white/75">
          {user.completed_rentals} completed rental{user.completed_rentals === 1 ? "" : "s"}.{" "}
          {user.next_tier
            ? `${user.next_tier.remaining} more to reach ${user.next_tier.name}.`
            : "You have reached the top tier."}{" "}
          A rental counts once the owner confirms the item came back in good condition.
        </p>
      </section>

      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <section className="card p-6">
          <h2 className="flex items-center gap-2 text-lg font-extrabold"><GraduationCap className="size-5 text-slate" /> Student verification</h2>
          {user.is_student_verified ? (
            <p className="mt-3 flex items-center gap-2 text-sm text-emerald-700">
              <CheckCircle2 className="size-4" /> Verified with {user.college_email}. 5% student discount active.
            </p>
          ) : (
            <form onSubmit={verify} className="mt-3 space-y-3">
              <p className="text-sm text-navy/60">Enter your college email to unlock 5% off every rental.</p>
              <label htmlFor="cemail" className="sr-only">College email</label>
              <input id="cemail" type="email" className="input" placeholder="you@college.ac.in" value={email} onChange={(e) => setEmail(e.target.value)} required />
              <button className="btn-dark w-full">Verify</button>
              <p className="text-xs text-navy/50">Prototype: we check the domain only. Production would send a one-time code.</p>
            </form>
          )}
        </section>

        <form onSubmit={save} className="card space-y-3 p-6">
          <h2 className="text-lg font-extrabold">Profile</h2>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="label" htmlFor="fn">First name</label><input id="fn" className="input" value={form.first_name} onChange={(e) => setForm({ ...form, first_name: e.target.value })} /></div>
            <div><label className="label" htmlFor="ln">Last name</label><input id="ln" className="input" value={form.last_name} onChange={(e) => setForm({ ...form, last_name: e.target.value })} /></div>
          </div>
          <div><label className="label" htmlFor="ph">Mobile</label><input id="ph" className="input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></div>
          <button className="btn-ghost w-full">Save changes</button>
        </form>
      </div>
    </div>
  );
}
