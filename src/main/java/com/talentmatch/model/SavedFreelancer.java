package com.talentmatch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_saved_freelancers", uniqueConstraints =
        @UniqueConstraint(columnNames = {"customer_id", "freelancer_id"}))
public class SavedFreelancer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private User freelancer;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }
    public User getFreelancer() { return freelancer; }
    public void setFreelancer(User freelancer) { this.freelancer = freelancer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
