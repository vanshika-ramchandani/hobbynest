import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { Menu, X, LogOut, FlaskConical } from "lucide-react";
import { useAuth } from "../auth";
import { DEMO_MODE } from "../api";

function DemoBanner() {
  if (!DEMO_MODE) return null;
  const reset = async () => {
    const { resetDemo } = await import("../demo/db");
    resetDemo();
    window.location.assign(import.meta.env.BASE_URL);
  };
  return (
    <div className="bg-terracotta-100 px-4 py-2 text-center text-xs font-medium text-navy sm:text-sm">
      <FlaskConical className="mr-1 inline size-4 align-text-bottom text-terracotta-700" aria-hidden="true" />
      Demo version: everything runs in your browser and your data is saved only on this device. Payments are test-only.{" "}
      <button onClick={reset} className="font-bold text-terracotta-700 underline">
        Reset demo data
      </button>
    </div>
  );
}

function Logo({ light }) {
  return (
    <Link to="/" className="flex items-center gap-2" aria-label="HobbyNest home">
      <img src={`${import.meta.env.BASE_URL}favicon.svg`} alt="" className="size-8" />
      <span className={`text-xl font-extrabold tracking-tight ${light ? "text-white" : ""}`}>
        Hobby<span className="text-terracotta">Nest</span>
      </span>
    </Link>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);

  const links = [
    { to: "/browse", label: "Browse" },
    { to: "/#how", label: "How it works" },
    ...(user ? [{ to: "/bookings", label: "My rentals" }] : []),
    ...(user?.role === "owner" ? [{ to: "/owner", label: "Owner dashboard" }] : []),
  ];

  const doLogout = () => {
    logout();
    setOpen(false);
    nav("/");
  };

  const linkCls = ({ isActive }) =>
    `rounded-lg px-3 py-2 text-sm font-semibold transition ${
      isActive ? "bg-white/10 text-white" : "text-white/75 hover:text-white"
    }`;
  // Hash links ("/#how") would otherwise look active on the home page.
  const clsFor = (l) => (l.to.includes("#") ? () => linkCls({ isActive: false }) : linkCls);

  return (
    <div className="flex min-h-screen flex-col">
      <DemoBanner />
      <header className="sticky top-0 z-40 bg-navy/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <Logo light />
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} className={clsFor(l)} end>
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="hidden items-center gap-2 md:flex">
            {user ? (
              <>
                <Link to="/profile" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-white hover:bg-white/10">
                  <span className="flex size-7 items-center justify-center rounded-full bg-terracotta text-xs font-bold text-navy">
                    {(user.first_name || user.username)[0].toUpperCase()}
                  </span>
                  {user.first_name || user.username}
                </Link>
                <button onClick={doLogout} className="rounded-lg p-2 text-white/70 hover:bg-white/10 hover:text-white" aria-label="Log out">
                  <LogOut className="size-4" />
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="px-3 py-2 text-sm font-semibold text-white/80 hover:text-white">
                  Log in
                </Link>
                <Link to="/register" className="btn-primary py-2 text-sm">
                  Sign up
                </Link>
              </>
            )}
          </div>
          <button className="rounded-lg p-2 text-white md:hidden" onClick={() => setOpen(!open)} aria-label="Toggle menu" aria-expanded={open}>
            {open ? <X /> : <Menu />}
          </button>
        </div>
        {open && (
          <div className="border-t border-white/10 px-4 pb-4 md:hidden">
            <nav className="flex flex-col py-2" aria-label="Mobile">
              {links.map((l) => (
                <NavLink key={l.to} to={l.to} className={clsFor(l)} onClick={() => setOpen(false)}>
                  {l.label}
                </NavLink>
              ))}
              {user && (
                <NavLink to="/profile" className={linkCls} onClick={() => setOpen(false)}>
                  Profile and rewards
                </NavLink>
              )}
            </nav>
            {user ? (
              <button onClick={doLogout} className="btn-ghost w-full">
                <LogOut className="size-4" /> Log out
              </button>
            ) : (
              <div className="grid grid-cols-2 gap-2">
                <Link to="/login" onClick={() => setOpen(false)} className="btn-ghost">Log in</Link>
                <Link to="/register" onClick={() => setOpen(false)} className="btn-primary">Sign up</Link>
              </div>
            )}
          </div>
        )}
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="mt-20 bg-navy text-white/70">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-12 sm:grid-cols-2 sm:px-6 lg:grid-cols-4">
          <div>
            <Logo light />
            <p className="mt-3 text-sm">Rent it. Try it. Love it.</p>
            <p className="mt-1 text-sm">Piloting in Ahmedabad.</p>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-bold text-white">Explore</h3>
            <ul className="space-y-2 text-sm">
              <li><Link to="/browse?category=music" className="hover:text-white">Musical instruments</Link></li>
              <li><Link to="/browse?category=photography" className="hover:text-white">Photography</Link></li>
              <li><Link to="/browse?category=sports" className="hover:text-white">Sports</Link></li>
              <li><Link to="/browse?rent_to_buy=1" className="hover:text-white">Rent-to-Buy items</Link></li>
            </ul>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-bold text-white">Owners</h3>
            <ul className="space-y-2 text-sm">
              <li><Link to="/register?role=owner" className="hover:text-white">List your equipment</Link></li>
              <li><Link to="/owner" className="hover:text-white">Owner dashboard</Link></li>
            </ul>
          </div>
          <div>
            <h3 className="mb-3 text-sm font-bold text-white">Policies</h3>
            <ul className="space-y-2 text-sm">
              <li><Link to="/policies#privacy" className="hover:text-white">Privacy policy</Link></li>
              <li><Link to="/policies#terms" className="hover:text-white">Terms and conditions</Link></li>
              <li><Link to="/policies#refunds" className="hover:text-white">Cancellations and refunds</Link></li>
              <li><Link to="/policies#damage" className="hover:text-white">Damage and disputes</Link></li>
            </ul>
          </div>
        </div>
        <div className="border-t border-white/10 py-4 text-center text-xs text-white/50">
          College prototype. Payments run in test mode only; no real money is charged.
        </div>
      </footer>
    </div>
  );
}
