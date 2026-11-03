package com.talentmatch.repository;

import com.talentmatch.model.SavedFreelancer;
import com.talentmatch.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedFreelancerRepository extends JpaRepository<SavedFreelancer, Long> {
    List<SavedFreelancer> findByCustomer(User customer);
    Optional<SavedFreelancer> findByCustomerAndFreelancer(User customer, User freelancer);
    long countByCustomer(User customer);
}
