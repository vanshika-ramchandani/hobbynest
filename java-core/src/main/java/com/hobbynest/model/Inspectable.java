package com.hobbynest.model;

import java.util.List;

/** Equipment the owner must inspect before dispatch and after return. */
public interface Inspectable {

    /** Category-specific checks the owner performs before handing the item to the delivery partner. */
    List<String> dispatchChecklist();

    /** Whether the renter must complete an ID check before this item ships. */
    boolean requiresIdVerification();
}
