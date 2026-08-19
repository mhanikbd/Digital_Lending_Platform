package com.naztech.lending.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One address of one kind. A customer holds at most one of each. */
@Entity
@Table(schema = "customer", name = "t_customer_address")
public class CustomerAddress {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(length = 255)
    private String line2;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String district;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(nullable = false, length = 60)
    private String country = "Bangladesh";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected CustomerAddress() {
        // for JPA
    }

    public CustomerAddress(AddressType addressType, String line1, String city, String district) {
        this.addressType = addressType;
        this.line1 = line1;
        this.city = city;
        this.district = district;
    }

    /** Set by Customer.addAddress so both ends of the relation agree. */
    void attachTo(Customer customer) {
        this.customer = customer;
    }

    /** One line, as a letter would carry it. */
    public String formatted() {
        StringBuilder text = new StringBuilder(line1);
        if (line2 != null && !line2.isBlank()) {
            text.append(", ").append(line2);
        }
        if (city != null && !city.isBlank()) {
            text.append(", ").append(city);
        }
        if (district != null && !district.isBlank() && !district.equals(city)) {
            text.append(", ").append(district);
        }
        return text.toString();
    }

    public UUID getId() {
        return id;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public String getLine1() {
        return line1;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }
}
