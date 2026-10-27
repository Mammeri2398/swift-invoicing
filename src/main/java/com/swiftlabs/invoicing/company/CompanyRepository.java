package com.swiftlabs.invoicing.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByActiveTrueOrderByName();

    List<Company> findAllByOrderByActiveDescNameAsc();

    long countByActiveTrue();
}
