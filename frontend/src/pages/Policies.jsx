import { PageHeader } from "../components/ui";

const SECTIONS = [
  {
    id: "privacy",
    title: "Privacy policy",
    body: [
      "We collect only what we need to run a rental: your name, email, mobile number, delivery address and booking history. Student verification stores your college email only.",
      "We do not store card numbers or UPI PINs. Payments are handled by the payment gateway.",
      "Your data is used to process bookings, deliveries, deposits and support requests. We do not sell or share it for advertising.",
      "You can ask us to correct or delete your data at any time, in line with the Digital Personal Data Protection Act, 2023.",
      "Data is encrypted in transit (HTTPS) and at rest in our managed cloud database.",
    ],
  },
  {
    id: "terms",
    title: "Terms and conditions",
    body: [
      "HobbyNest is a marketplace connecting renters with equipment owners, studios and academies. Owners remain responsible for the accuracy of their listings.",
      "Renters must use equipment with reasonable care and return it on the agreed date with all accessories.",
      "HobbyNest earns a 15% commission on rentals and 8% on Rent-to-Buy sales. This is deducted from the owner's share and never added on top of the price you see.",
      "Reviews can only be written by renters who completed a rental of that item. We do not edit, buy or suppress reviews.",
    ],
  },
  {
    id: "refunds",
    title: "Cancellations and refunds",
    body: [
      "Cancel any time before the item is delivered for a full refund to your original payment method.",
      "Unpaid bookings hold the dates for 15 minutes, then the dates are released.",
      "Refundable deposits are returned after the owner confirms the item came back in acceptable condition.",
    ],
  },
  {
    id: "damage",
    title: "Damage and disputes",
    body: [
      "Owners record the item's condition before dispatch. On return they inspect it and report any damage with photos.",
      "Valid repair costs may be deducted from the deposit. Either party can raise a dispute, which our support team reviews with the evidence from both sides.",
      "Late returns are charged at the daily rate for each extra day.",
    ],
  },
  {
    id: "rent-to-buy",
    title: "Rent-to-Buy",
    body: [
      "Owners may choose to sell an item they list. The sale price is shown upfront on the listing.",
      "If you rent the item first, 50% of the rent you paid is credited toward the sale price, capped at half the sale price.",
      "The sale completes only when the owner accepts your request. Ownership transfers after payment.",
    ],
  },
];

export default function Policies() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <PageHeader title="Policies" subtitle="Plain-language rules for renters and owners." />
      <div className="space-y-6">
        {SECTIONS.map((s) => (
          <section key={s.id} id={s.id} className="card scroll-mt-24 p-6">
            <h2 className="text-xl font-extrabold">{s.title}</h2>
            <ul className="mt-3 list-disc space-y-2 pl-5 text-navy/75">
              {s.body.map((p) => <li key={p}>{p}</li>)}
            </ul>
          </section>
        ))}
      </div>
      <p className="mt-6 text-xs text-navy/50">Prototype policy text for a college project. Final policies should be reviewed by a legal professional.</p>
    </div>
  );
}
