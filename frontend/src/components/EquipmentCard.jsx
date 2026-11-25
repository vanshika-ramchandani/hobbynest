import { Link } from "react-router-dom";
import { Repeat, Sparkles } from "lucide-react";
import { inr } from "../api";
import { ItemVisual, Rating } from "./ui";

export default function EquipmentCard({ item }) {
  return (
    <Link
      to={`/equipment/${item.id}`}
      className="card group flex flex-col overflow-hidden transition hover:-translate-y-0.5 hover:shadow-lg"
    >
      <div className="relative">
        <ItemVisual item={item} className="aspect-[4/3] w-full transition group-hover:scale-[1.02]" />
        <div className="absolute left-3 top-3 flex flex-wrap gap-1.5">
          {item.rent_to_buy && (
            <span className="chip bg-terracotta text-navy">
              <Repeat className="size-3" /> Rent-to-Buy
            </span>
          )}
          {item.beginner_friendly && (
            <span className="chip bg-white/90 text-navy">
              <Sparkles className="size-3" /> Beginner
            </span>
          )}
        </div>
      </div>
      <div className="flex flex-1 flex-col p-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate">{item.category_name}</p>
        <h3 className="mt-1 line-clamp-2 font-bold leading-snug">{item.title}</h3>
        <p className="mt-1 text-xs text-navy/50">by {item.owner_name}</p>
        <div className="mt-auto flex items-end justify-between pt-4">
          <div>
            <span className="text-xl font-extrabold">{inr(item.daily_rate)}</span>
            <span className="text-sm text-navy/50">/day</span>
            <p className="text-xs text-navy/50">{inr(item.weekly_rate)}/week</p>
          </div>
          <Rating value={item.rating} count={item.review_count} />
        </div>
      </div>
    </Link>
  );
}
