package com.bistrobyte.orderservice.domain;

/** The three sales channels BistroByte serves. */
public enum OrderChannel {

    /** Eaten in the restaurant; requires a table number. */
    DINE_IN,

    /** Collected at the counter; requires a contact phone number. */
    TAKEAWAY,

    /** Delivered to the customer; requires an address, a phone number and a delivery fee. */
    DELIVERY
}
