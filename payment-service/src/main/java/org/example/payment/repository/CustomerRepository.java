package org.example.payment.repository;

import org.example.payment.domain.Customer;
import org.springframework.data.repository.CrudRepository;

public interface CustomerRepository extends CrudRepository<Customer, Long> {
    // save variants
    // exists by id
    // find variants
    // count
    // delete variants
}
