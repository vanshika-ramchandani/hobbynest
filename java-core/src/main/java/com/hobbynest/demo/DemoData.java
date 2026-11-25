package com.hobbynest.demo;

import com.hobbynest.exception.HobbyNestException;
import com.hobbynest.model.Category;
import com.hobbynest.model.Condition;
import com.hobbynest.model.EquipmentFactory;
import com.hobbynest.model.Owner;
import com.hobbynest.service.RentalService;

/** Seeds a fresh data folder with owners, a student renter and a small catalogue. */
public final class DemoData {

    private DemoData() {
    }

    public static void seedIfEmpty(RentalService service) throws HobbyNestException {
        if (!service.listEquipment().isEmpty()) {
            return;
        }
        Owner studio = service.addOwner("rhythm_studio", "rhythm.studio@hobbynest.test", Owner.OwnerType.STUDIO);
        Owner lens = service.addOwner("lensbox", "lensbox@hobbynest.test", Owner.OwnerType.STUDIO);
        Owner academy = service.addOwner("smash_academy", "smash.academy@hobbynest.test", Owner.OwnerType.ACADEMY);
        Owner kavya = service.addOwner("kavya_owner", "kavya.owner@hobbynest.test", Owner.OwnerType.INDIVIDUAL);
        service.addRenter("demo_student", "student@hobbynest.test", true);

        Object[][] items = {
                {studio, Category.MUSIC, "Yamaha F310 Acoustic Guitar", Condition.GOOD, 120, 600, 1800, 9000, 7000},
                {studio, Category.MUSIC, "Casio CT-S300 Keyboard (61 keys)", Condition.NEW, 150, 800, 2400, 11000, 8500},
                {studio, Category.MUSIC, "Pioneer DDJ-400 DJ Controller", Condition.GOOD, 450, 2200, 6500, 28000, null},
                {lens, Category.PHOTOGRAPHY, "Canon EOS 1500D + 18-55mm Kit", Condition.GOOD, 500, 2500, 7500, 32000, 26000},
                {lens, Category.PHOTOGRAPHY, "DJI Osmo Mobile 6 Gimbal", Condition.GOOD, 200, 1000, 3000, 13000, 10000},
                {academy, Category.SPORTS, "Yonex Badminton Kit (2 rackets + shuttles)", Condition.GOOD, 80, 400, 1200, 4000, null},
                {academy, Category.SPORTS, "Stag Table Tennis Set", Condition.NEW, 50, 250, 700, 2200, 1800},
                {kavya, Category.ART, "Electric Pottery Wheel", Condition.GOOD, 300, 1500, 4500, 18000, 14000},
                {kavya, Category.GAMING, "PlayStation 5 + 2 Controllers", Condition.GOOD, 600, 3200, 9500, 52000, null},
        };
        for (Object[] it : items) {
            Owner o = (Owner) it[0];
            service.addEquipment(id -> EquipmentFactory.create((Category) it[1], id, o.getId(), (String) it[2],
                    (Condition) it[3], (int) it[4], (int) it[5], (int) it[6], (int) it[7], (Integer) it[8]));
        }
    }
}
