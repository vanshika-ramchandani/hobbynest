import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md px-4 py-24 text-center">
      <p className="text-6xl font-extrabold text-terracotta">404</p>
      <h1 className="mt-4 text-2xl font-extrabold">Page not found</h1>
      <Link to="/" className="btn-primary mt-6">Go home</Link>
    </div>
  );
}
