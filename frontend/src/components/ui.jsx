import { Camera, Gamepad2, Music, Palette, Trophy, Package, Star, Loader2, AlertCircle, CheckCircle2 } from "lucide-react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth";

export const CATEGORY_STYLE = {
  music: { icon: Music, from: "#3D5A80", to: "#1B263B" },
  photography: { icon: Camera, from: "#E07A5F", to: "#9C4A35" },
  sports: { icon: Trophy, from: "#5B8A72", to: "#2E4C3E" },
  art: { icon: Palette, from: "#C98B4F", to: "#7A4E26" },
  gaming: { icon: Gamepad2, from: "#6B5B95", to: "#352B52" },
};

export function CategoryIcon({ slug, className = "size-5" }) {
  const Icon = CATEGORY_STYLE[slug]?.icon || Package;
  return <Icon className={className} aria-hidden="true" />;
}

/** Generated artwork per category, so the demo needs no third-party photos. */
export function ItemVisual({ item, className = "" }) {
  const s = CATEGORY_STYLE[item.category_slug] || { from: "#3D5A80", to: "#1B263B" };
  if (item.image_url) {
    return <img src={item.image_url} alt={item.title} className={`object-cover ${className}`} loading="lazy" />;
  }
  return (
    <div
      className={`relative flex items-center justify-center overflow-hidden ${className}`}
      style={{ background: `linear-gradient(135deg, ${s.from}, ${s.to})` }}
      role="img"
      aria-label={item.title}
    >
      <div className="absolute -right-8 -top-8 size-32 rounded-full bg-white/10" />
      <div className="absolute -bottom-10 -left-6 size-28 rounded-full bg-white/5" />
      <CategoryIcon slug={item.category_slug} className="size-14 text-white/90 drop-shadow" />
    </div>
  );
}

export function Rating({ value, count }) {
  if (!value) return <span className="text-xs text-navy/50">No reviews yet</span>;
  return (
    <span className="inline-flex items-center gap-1 text-sm font-semibold">
      <Star className="size-4 fill-terracotta text-terracotta" aria-hidden="true" />
      {value}
      <span className="font-normal text-navy/50">({count})</span>
    </span>
  );
}

export function Spinner({ label = "Loading" }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-navy/60" role="status">
      <Loader2 className="size-5 animate-spin" /> {label}...
    </div>
  );
}

export function Alert({ kind = "error", children }) {
  if (!children) return null;
  const ok = kind === "success";
  const Icon = ok ? CheckCircle2 : AlertCircle;
  return (
    <div
      role={ok ? "status" : "alert"}
      className={`flex items-start gap-2 rounded-xl px-4 py-3 text-sm font-medium ${
        ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"
      }`}
    >
      <Icon className="mt-0.5 size-4 shrink-0" /> <span>{children}</span>
    </div>
  );
}

const STATUS_STYLES = {
  pending_payment: "bg-amber-100 text-amber-800",
  payment_failed: "bg-red-100 text-red-800",
  confirmed: "bg-slate-100 text-slate",
  active: "bg-terracotta-100 text-terracotta-700",
  returned: "bg-violet-100 text-violet-800",
  completed: "bg-emerald-100 text-emerald-800",
  cancelled: "bg-navy/10 text-navy/60",
};

export function StatusBadge({ status, label }) {
  return <span className={`chip ${STATUS_STYLES[status] || "bg-navy/10"}`}>{label}</span>;
}

export function RequireAuth({ children, role }) {
  const { user, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <Spinner />;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return children;
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">{title}</h1>
        {subtitle && <p className="mt-2 text-navy/60">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}
