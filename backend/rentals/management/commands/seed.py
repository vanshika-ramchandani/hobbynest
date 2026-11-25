"""Seed demo data: categories, owners, equipment, a demo renter and some past reviews.

Passwords are read from the DEMO_PASSWORD environment variable (never hard-coded).
Usage: python manage.py seed [--reset]
"""
import os
from datetime import date, timedelta

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from accounts.models import User
from rentals.models import Booking, Category, Equipment, Review
from rentals.services import quote

CATEGORIES = [
    ("Musical Instruments", "music", "music", "Guitars, keyboards, ukuleles and more"),
    ("Photography", "photography", "camera", "Cameras, lenses, tripods and lights"),
    ("Sports", "sports", "trophy", "Badminton, cricket, table tennis and tennis"),
    ("Art & Creative", "art", "palette", "Easels, pottery wheels and craft tools"),
    ("Gaming & Tech", "gaming", "gamepad", "Consoles, VR and DJ controllers"),
]

OWNERS = [
    ("rhythm_studio", "Rhythm", "Music Studio", "rhythm.studio@hobbynest.test"),
    ("lensbox", "LensBox", "Rentals", "lensbox@hobbynest.test"),
    ("smash_academy", "Smash", "Sports Academy", "smash.academy@hobbynest.test"),
    ("kavya_owner", "Kavya", "Shah", "kavya.owner@hobbynest.test"),
]

# (owner idx, category slug, title, desc, specs, condition, daily, weekly, monthly, value, r2b sale, beginner)
ITEMS = [
    (0, "music", "Yamaha F310 Acoustic Guitar", "Full-size acoustic guitar, perfect for beginners learning chords. Comes with gig bag, capo and picks.", "Spruce top, steel strings, 6 strings", "good", 120, 600, 1800, 9000, 7000, True),
    (0, "music", "Casio CT-S300 Keyboard (61 keys)", "Portable keyboard with touch-sensitive keys and built-in lessons. Adapter and stand included.", "61 keys, 400 tones, lesson mode", "new", 150, 800, 2400, 11000, 8500, True),
    (0, "music", "Kala Soprano Ukulele", "Lightweight ukulele, easiest string instrument to start with. Tuner included.", "Mahogany, Aquila strings", "good", 60, 300, 900, 3500, 2600, True),
    (0, "music", "Pioneer DDJ-400 DJ Controller", "2-channel rekordbox controller. Try DJing before investing. Headphones included.", "2 decks, built-in mixer, USB", "good", 450, 2200, 6500, 28000, None, False),
    (1, "photography", "Canon EOS 1500D + 18-55mm Kit", "Entry-level DSLR ideal for learning manual photography. Two batteries and 32GB card.", "24.1MP APS-C, Full HD video", "good", 500, 2500, 7500, 32000, 26000, True),
    (1, "photography", "Sony A7 III Mirrorless (body)", "Full-frame mirrorless for creators and freelancers. ID verification required.", "24.2MP full frame, 4K video, IBIS", "new", 1800, 9500, 28000, 140000, None, False),
    (1, "photography", "DJI Osmo Mobile 6 Gimbal", "Smartphone gimbal for smooth reels and vlogs.", "3-axis, ActiveTrack 6.0", "good", 200, 1000, 3000, 13000, 10000, True),
    (1, "photography", "Godox SL60W LED Light + Softbox", "Continuous studio light for product shoots and portraits.", "60W, 5600K, Bowens mount", "fair", 250, 1200, 3500, 9000, None, False),
    (2, "sports", "Yonex Badminton Kit (2 rackets + shuttles)", "Two Yonex Nanoray rackets and a tube of nylon shuttles.", "Nanoray series, Mavis 350 shuttles", "good", 80, 400, 1200, 4000, None, True),
    (2, "sports", "SS Cricket Kit (Full, adult)", "Bat, pads, gloves, helmet and kit bag. Sanitised after every rental.", "English willow bat, size SH", "good", 250, 1300, 3800, 15000, None, False),
    (2, "sports", "Stag Table Tennis Set", "Two rackets, clamp-on net and 6 balls. Fits any table.", "Stag Iconic, 3-star balls", "new", 50, 250, 700, 2200, 1800, True),
    (2, "sports", "Head Tennis Racket Pair", "Two Head Ti racket with cover and 3 balls.", "Titanium frame, 4 3/8 grip", "good", 100, 500, 1500, 6000, None, True),
    (3, "art", "Studio Easel + Acrylic Starter Set", "Wooden H-frame easel with 24 acrylic colours, brushes and 3 canvases.", "Adjustable up to 150cm", "good", 90, 450, 1300, 5000, None, True),
    (3, "art", "Electric Pottery Wheel", "Quiet pottery wheel with splash pan. Clay sold separately.", "350W, 25cm wheel head, 0-300 rpm", "good", 300, 1500, 4500, 18000, 14000, False),
    (3, "gaming", "PlayStation 5 + 2 Controllers", "PS5 disc edition with two DualSense controllers and 3 games.", "825GB SSD, 4K output", "good", 600, 3200, 9500, 52000, None, False),
    (3, "gaming", "Meta Quest 3 VR Headset", "Standalone VR headset. Fitness, games and creative apps preinstalled.", "128GB, mixed reality", "new", 550, 2800, 8000, 48000, 40000, False),
]

REVIEWS = [
    (0, 5, "Guitar was in great shape and the capo was a nice touch. Bought my own after 3 weeks."),
    (4, 5, "Perfect for my first photography class. Owner explained all the settings."),
    (8, 4, "Rackets were good, shuttles slightly used. Delivery on time."),
    (14, 5, "Weekend PS5 rental for a hostel tournament. Smooth pickup and return."),
]


class Command(BaseCommand):
    help = "Seed HobbyNest demo data"

    def add_arguments(self, parser):
        parser.add_argument("--reset", action="store_true", help="Delete existing demo data first")

    @transaction.atomic
    def handle(self, *args, **opts):
        password = os.environ.get("DEMO_PASSWORD")
        if not password:
            raise CommandError("Set DEMO_PASSWORD in your environment or .env file first.")

        if opts["reset"]:
            Review.objects.all().delete()
            Booking.objects.all().delete()
            Equipment.objects.all().delete()
            Category.objects.all().delete()
            User.objects.filter(email__endswith="@hobbynest.test").delete()

        cats = {}
        for name, slug, icon, desc in CATEGORIES:
            cats[slug], _ = Category.objects.update_or_create(
                slug=slug, defaults={"name": name, "icon": icon, "description": desc})

        def make_user(username, first, last, email, role, **extra):
            u, created = User.objects.get_or_create(username=username, defaults={
                "first_name": first, "last_name": last, "email": email, "role": role, **extra})
            if created:
                u.set_password(password)
                u.save()
            return u

        owners = [make_user(u, f, l, e, "owner") for u, f, l, e in OWNERS]
        renter = make_user("demo_student", "Aarav", "Patel", "student@hobbynest.test", "renter",
                           college_email="aarav@ldce.ac.in", is_student_verified=True)
        reviewers = [make_user(f"renter{i}", n, "", f"renter{i}@hobbynest.test", "renter")
                     for i, n in enumerate(["Diya", "Kabir", "Meera", "Rohan"], 1)]
        admin = make_user("admin", "Site", "Admin", "admin@hobbynest.test", "renter",
                          is_staff=True, is_superuser=True)

        items = []
        for o, cat, title, desc, specs, cond, d, w, m, value, sale, beginner in ITEMS:
            eq, _ = Equipment.objects.update_or_create(title=title, owner=owners[o], defaults={
                "category": cats[cat], "description": desc, "specs": specs, "condition": cond,
                "daily_rate": d, "weekly_rate": w, "monthly_rate": m, "market_value": value,
                "rent_to_buy": sale is not None, "sale_price": sale, "beginner_friendly": beginner})
            items.append(eq)

        # Past completed rentals with reviews, so ratings show on the catalogue.
        if not Review.objects.exists():
            for (idx, rating, text), reviewer in zip(REVIEWS, reviewers):
                eq = items[idx]
                start = date.today() - timedelta(days=40)
                end = start + timedelta(days=6)
                q = quote(eq, start, end, today=start)
                q.pop("deposit_label")
                b = Booking.objects.create(renter=reviewer, equipment=eq, start_date=start, end_date=end,
                                           delivery_address="Demo address, Navrangpura, Ahmedabad",
                                           status=Booking.Status.COMPLETED, deposit_refunded=True, **q)
                Review.objects.create(booking=b, equipment=eq, author=reviewer, rating=rating, comment=text)
                reviewer.completed_rentals += 1
                reviewer.save(update_fields=["completed_rentals"])

        self.stdout.write(self.style.SUCCESS(
            f"Seeded {len(cats)} categories, {len(items)} items. "
            f"Logins (password from DEMO_PASSWORD): demo_student, kavya_owner, rhythm_studio, admin"))
