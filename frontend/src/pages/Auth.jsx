import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth";
import { Alert } from "../components/ui";
import { DEMO_MODE } from "../api";

function Shell({ title, subtitle, children }) {
  return (
    <div className="mx-auto flex max-w-md flex-col px-4 py-14">
      <h1 className="text-3xl font-extrabold">{title}</h1>
      <p className="mt-1 text-navy/60">{subtitle}</p>
      <div className="card mt-8 p-6">{children}</div>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [f, setF] = useState({ username: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const signIn = async (username, password) => {
    setBusy(true);
    setError("");
    try {
      const u = await login(username, password);
      const next = params.get("next");
      nav(next && next.startsWith("/") ? next : u?.role === "owner" ? "/owner" : "/browse");
    } catch (err) {
      setError(err.status === 401 ? "Incorrect username or password." : err.message);
    } finally {
      setBusy(false);
    }
  };

  const submit = (e) => {
    e.preventDefault();
    signIn(f.username.trim(), f.password);
  };

  return (
    <Shell title="Welcome back" subtitle="Log in to book and manage your rentals.">
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="label" htmlFor="u">Username</label>
          <input id="u" className="input" autoComplete="username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} required />
        </div>
        <div>
          <label className="label" htmlFor="p">Password</label>
          <input id="p" type="password" className="input" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required />
        </div>
        {error && <Alert>{error}</Alert>}
        <button className="btn-primary w-full py-3" disabled={busy}>{busy ? "Logging in..." : "Log in"}</button>
      </form>
      {DEMO_MODE && (
        <div className="mt-6 rounded-xl bg-sand p-4">
          <p className="text-sm font-bold">Just looking? Try a demo account</p>
          <p className="mt-1 text-xs text-navy/60">No password needed. Demo data is stored only in this browser.</p>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <button type="button" className="btn-dark py-2 text-sm" disabled={busy} onClick={() => signIn("demo_student", "")}>
              Student renter
            </button>
            <button type="button" className="btn-ghost py-2 text-sm" disabled={busy} onClick={() => signIn("kavya_owner", "")}>
              Equipment owner
            </button>
          </div>
        </div>
      )}
      <p className="mt-6 text-center text-sm text-navy/60">
        New here? <Link to="/register" className="font-bold text-terracotta-700 hover:underline">Create an account</Link>
      </p>
    </Shell>
  );
}

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [f, setF] = useState({
    first_name: "", last_name: "", username: "", email: "", phone: "", password: "",
    role: params.get("role") === "owner" ? "owner" : "renter",
    consent: false,
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (!f.consent) return setError("Please agree to the Terms and Privacy Policy to continue.");
    setBusy(true);
    setError("");
    try {
      const u = await register(f);
      nav(u.role === "owner" ? "/owner" : "/browse");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell title="Join HobbyNest" subtitle="Rent gear to try new hobbies, or earn from gear you own.">
      <form onSubmit={submit} className="space-y-4">
        <fieldset>
          <legend className="label">I want to</legend>
          <div className="grid grid-cols-2 gap-2">
            {[["renter", "Rent equipment"], ["owner", "List my equipment"]].map(([v, l]) => (
              <label key={v} className={`cursor-pointer rounded-xl border-2 p-3 text-center text-sm font-semibold ${f.role === v ? "border-terracotta bg-terracotta-100/50" : "border-navy/10"}`}>
                <input type="radio" name="role" value={v} checked={f.role === v} onChange={set("role")} className="sr-only" />
                {l}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label" htmlFor="fn">First name</label><input id="fn" className="input" value={f.first_name} onChange={set("first_name")} required /></div>
          <div><label className="label" htmlFor="ln">Last name</label><input id="ln" className="input" value={f.last_name} onChange={set("last_name")} /></div>
        </div>
        <div><label className="label" htmlFor="un">Username</label><input id="un" className="input" autoComplete="username" value={f.username} onChange={set("username")} required /></div>
        <div><label className="label" htmlFor="em">Email</label><input id="em" type="email" className="input" autoComplete="email" value={f.email} onChange={set("email")} required /></div>
        <div><label className="label" htmlFor="ph">Mobile (optional)</label><input id="ph" className="input" inputMode="numeric" placeholder="10 digits" value={f.phone} onChange={set("phone")} /></div>
        <div>
          <label className="label" htmlFor="pw">Password</label>
          <input id="pw" type="password" className="input" autoComplete="new-password" value={f.password} onChange={set("password")} required minLength={8} />
          <p className="mt-1 text-xs text-navy/50">At least 8 characters, not all numbers.</p>
        </div>
        <label className="flex items-start gap-2 text-sm text-navy/75">
          <input type="checkbox" className="mt-1 accent-terracotta" checked={f.consent} onChange={set("consent")} />
          <span>
            I agree to the <Link to="/policies#terms" className="font-semibold underline" target="_blank">Terms</Link> and{" "}
            <Link to="/policies#privacy" className="font-semibold underline" target="_blank">Privacy Policy</Link>, and consent to HobbyNest
            processing my details to manage rentals.
          </span>
        </label>
        {error && <Alert>{error}</Alert>}
        <button className="btn-primary w-full py-3" disabled={busy}>{busy ? "Creating account..." : "Create account"}</button>
        <p className="text-xs text-navy/50">We never send marketing messages unless you opt in later.</p>
      </form>
      <p className="mt-6 text-center text-sm text-navy/60">
        Already have an account? <Link to="/login" className="font-bold text-terracotta-700 hover:underline">Log in</Link>
      </p>
    </Shell>
  );
}
