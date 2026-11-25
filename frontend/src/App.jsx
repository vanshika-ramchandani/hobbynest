import { useEffect } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import Layout from "./components/Layout";
import { RequireAuth } from "./components/ui";
import Home from "./pages/Home";
import Browse from "./pages/Browse";
import Detail from "./pages/Detail";
import Checkout from "./pages/Checkout";
import Bookings from "./pages/Bookings";
import Profile from "./pages/Profile";
import OwnerDashboard from "./pages/OwnerDashboard";
import { Login, Register } from "./pages/Auth";
import Policies from "./pages/Policies";
import NotFound from "./pages/NotFound";

function ScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (hash) {
      document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: "smooth" });
    } else {
      window.scrollTo(0, 0);
    }
  }, [pathname, hash]);
  return null;
}

export default function App() {
  return (
    <>
      <ScrollManager />
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="browse" element={<Browse />} />
          <Route path="equipment/:id" element={<Detail />} />
          <Route path="checkout/:bookingId" element={<RequireAuth><Checkout /></RequireAuth>} />
          <Route path="bookings" element={<RequireAuth><Bookings /></RequireAuth>} />
          <Route path="profile" element={<RequireAuth><Profile /></RequireAuth>} />
          <Route path="owner" element={<RequireAuth role="owner"><OwnerDashboard /></RequireAuth>} />
          <Route path="login" element={<Login />} />
          <Route path="register" element={<Register />} />
          <Route path="policies" element={<Policies />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </>
  );
}
