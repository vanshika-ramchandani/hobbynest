package com.hobbynest.storage;

import com.hobbynest.exception.DataStoreException;
import com.hobbynest.model.Booking;
import com.hobbynest.model.Equipment;
import com.hobbynest.model.User;

import java.util.List;

/** Persistence port. FileDataStore is the implementation; a database-backed one could replace it. */
public interface DataStore {

    record Snapshot(List<User> users, List<Equipment> equipment, List<Booking> bookings) {
    }

    Snapshot load() throws DataStoreException;

    void save(Snapshot snapshot) throws DataStoreException;
}
