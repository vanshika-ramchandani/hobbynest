package com.hobbynest.storage;

import com.hobbynest.exception.DataStoreException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.BookingStatus;
import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.Owner;
import com.hobbynest.model.Quote;
import com.hobbynest.model.Renter;
import com.hobbynest.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads all data as CSV files in a directory (users.csv, equipment.csv, bookings.csv).
 * Writes go to a temp file first and are then moved into place, so a crash mid-write
 * never leaves a half-written file. save() is synchronized so concurrent saves cannot interleave.
 */
public class FileDataStore implements DataStore {

    static final String USERS = "users.csv";
    static final String EQUIPMENT = "equipment.csv";
    static final String BOOKINGS = "bookings.csv";

    private static final String USERS_HEADER =
            "id,role,username,email,student_verified,completed_rentals,owner_type";
    private static final String EQUIPMENT_HEADER =
            "id,category,owner_id,title,condition,daily,weekly,monthly,market_value,sale_price,active";
    private static final String BOOKINGS_HEADER = "id,equipment_id,renter_id,start,end,address,status,created_at,"
            + "payment_ref,deposit_refunded,days,rent,discount,discount_label,delivery,deposit,deposit_label,"
            + "total,commission,owner_payout,id_check";

    private final Path dir;

    public FileDataStore(Path dir) {
        this.dir = dir;
    }

    public Path getDirectory() {
        return dir;
    }

    @Override
    public synchronized Snapshot load() throws DataStoreException {
        try {
            Files.createDirectories(dir);
            return new Snapshot(loadUsers(), loadEquipment(), loadBookings());
        } catch (IOException e) {
            throw new DataStoreException("Could not read data from " + dir + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(Snapshot s) throws DataStoreException {
        try {
            Files.createDirectories(dir);
            List<String> users = new ArrayList<>();
            users.add(USERS_HEADER);
            for (User u : s.users()) {
                boolean renter = u instanceof Renter;
                users.add(CsvCodec.encode(List.of(
                        str(u.getId()), u.getRole(), u.getUsername(), u.getEmail(),
                        renter ? str(((Renter) u).isStudentVerified()) : "false",
                        renter ? str(((Renter) u).getCompletedRentals()) : "0",
                        u instanceof Owner o ? o.getOwnerType().name() : "")));
            }
            writeAtomically(USERS, users);

            List<String> eq = new ArrayList<>();
            eq.add(EQUIPMENT_HEADER);
            for (Equipment e : s.equipment()) {
                eq.add(CsvCodec.encode(List.of(
                        str(e.getId()), e.getCategory().name(), str(e.getOwnerId()), e.getTitle(),
                        e.getCondition().name(), str(e.getDailyRate()), str(e.getWeeklyRate()),
                        str(e.getMonthlyRate()), str(e.getMarketValue()),
                        e.getSalePrice() == null ? "" : str(e.getSalePrice()), str(e.isActive()))));
            }
            writeAtomically(EQUIPMENT, eq);

            List<String> bk = new ArrayList<>();
            bk.add(BOOKINGS_HEADER);
            for (Booking b : s.bookings()) {
                Quote q = b.getQuote();
                bk.add(CsvCodec.encode(List.of(
                        str(b.getId()), str(b.getEquipmentId()), str(b.getRenterId()),
                        b.getStartDate().toString(), b.getEndDate().toString(), b.getDeliveryAddress(),
                        b.getStatus().name(), b.getCreatedAt().toString(),
                        b.getPaymentReference() == null ? "" : b.getPaymentReference(),
                        str(b.isDepositRefunded()), str(q.days()), str(q.rentalAmount()), str(q.discountAmount()),
                        q.discountLabel(), str(q.deliveryFee()), str(q.deposit()), q.depositLabel(),
                        str(q.totalPayable()), str(q.commission()), str(q.ownerPayout()), str(q.idCheckRequired()))));
            }
            writeAtomically(BOOKINGS, bk);
        } catch (IOException e) {
            throw new DataStoreException("Could not save data to " + dir + ": " + e.getMessage(), e);
        }
    }

    private void writeAtomically(String name, List<String> lines) throws IOException {
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<List<String>> rows(String name) throws IOException {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) {
            return List.of();
        }
        List<List<String>> all = CsvCodec.parse(Files.readString(p, StandardCharsets.UTF_8));
        return all.isEmpty() ? all : all.subList(1, all.size()); // skip header
    }

    private List<User> loadUsers() throws IOException, DataStoreException {
        List<User> out = new ArrayList<>();
        for (List<String> r : rows(USERS)) {
            try {
                long id = Long.parseLong(r.get(0));
                if ("owner".equals(r.get(1))) {
                    out.add(new Owner(id, r.get(2), r.get(3), Owner.OwnerType.valueOf(r.get(6))));
                } else {
                    out.add(new Renter(id, r.get(2), r.get(3), Boolean.parseBoolean(r.get(4)),
                            Integer.parseInt(r.get(5))));
                }
            } catch (RuntimeException e) {
                throw new DataStoreException("Corrupt row in " + USERS + ": " + r, e);
            }
        }
        return out;
    }

    private List<Equipment> loadEquipment() throws IOException, DataStoreException {
        List<Equipment> out = new ArrayList<>();
        for (List<String> r : rows(EQUIPMENT)) {
            try {
                Equipment e = EquipmentFactory.create(Category.valueOf(r.get(1)), Long.parseLong(r.get(0)),
                        Long.parseLong(r.get(2)), r.get(3), Condition.valueOf(r.get(4)), Integer.parseInt(r.get(5)),
                        Integer.parseInt(r.get(6)), Integer.parseInt(r.get(7)), Integer.parseInt(r.get(8)),
                        r.get(9).isEmpty() ? null : Integer.valueOf(r.get(9)));
                e.setActive(Boolean.parseBoolean(r.get(10)));
                out.add(e);
            } catch (RuntimeException ex) {
                throw new DataStoreException("Corrupt row in " + EQUIPMENT + ": " + r, ex);
            }
        }
        return out;
    }

    private List<Booking> loadBookings() throws IOException, DataStoreException {
        List<Booking> out = new ArrayList<>();
        for (List<String> r : rows(BOOKINGS)) {
            try {
                Quote q = new Quote(Integer.parseInt(r.get(10)), Integer.parseInt(r.get(11)),
                        Integer.parseInt(r.get(12)), r.get(13), Integer.parseInt(r.get(14)),
                        Integer.parseInt(r.get(15)), r.get(16), Integer.parseInt(r.get(17)),
                        Integer.parseInt(r.get(18)), Integer.parseInt(r.get(19)), Boolean.parseBoolean(r.get(20)));
                out.add(new Booking(Long.parseLong(r.get(0)), Long.parseLong(r.get(1)), Long.parseLong(r.get(2)),
                        LocalDate.parse(r.get(3)), LocalDate.parse(r.get(4)), r.get(5),
                        q, Instant.parse(r.get(7)), BookingStatus.valueOf(r.get(6)),
                        r.get(8).isEmpty() ? null : r.get(8), Boolean.parseBoolean(r.get(9))));
            } catch (RuntimeException ex) {
                throw new DataStoreException("Corrupt row in " + BOOKINGS + ": " + r, ex);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return String.valueOf(o);
    }
}
