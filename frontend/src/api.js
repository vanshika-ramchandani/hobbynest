const BASE = import.meta.env.VITE_API_URL || "/api";

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

function firstError(data) {
  if (!data) return "Something went wrong.";
  if (typeof data === "string") return data;
  if (data.detail) return data.detail;
  const [field, val] = Object.entries(data)[0] || [];
  const msg = Array.isArray(val) ? val[0] : val;
  return field && field !== "non_field_errors" ? `${field.replace(/_/g, " ")}: ${msg}` : msg;
}

/**
 * Demo mode (used for the GitHub Pages build): no server, the whole API runs in the browser
 * and data is kept in localStorage. Local development and cloud deployments use the Django API.
 */
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";

async function demoApi(path, { method = "GET", body, auth = true } = {}) {
  const { mockApi } = await import("./demo/mockApi");
  const token = auth ? localStorage.getItem("hn_access") : null;
  try {
    // Clone so the in-browser "server" never shares objects with React state.
    return structuredClone(await mockApi(path, { method, body }, token));
  } catch (e) {
    if (e?.status) throw new ApiError(e.message, e.status, { detail: e.message, code: e.code });
    throw e;
  }
}

export async function api(path, options = {}) {
  if (DEMO_MODE) return demoApi(path, options);
  const { method = "GET", body, auth = true } = options;
  const headers = { "Content-Type": "application/json" };
  const token = localStorage.getItem("hn_access");
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  let res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });

  if (res.status === 401 && token && localStorage.getItem("hn_refresh")) {
    const r = await fetch(BASE + "/auth/refresh/", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh: localStorage.getItem("hn_refresh") }),
    });
    if (r.ok) {
      localStorage.setItem("hn_access", (await r.json()).access);
      return api(path, { method, body, auth });
    }
    localStorage.removeItem("hn_access");
    localStorage.removeItem("hn_refresh");
  }

  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(firstError(data), res.status, data);
  return data;
}

export const inr = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

export const todayISO = (offset = 0) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toLocaleDateString("en-CA");
};

export const fmtDate = (s) =>
  new Date(s + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
