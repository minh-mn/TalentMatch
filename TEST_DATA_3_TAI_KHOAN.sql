-- TalentMatch isolated 3-account testing dataset generated from user-provided dump.
-- BACK UP THE DATABASE: this script intentionally removes all other users and unrelated demo rows.
-- DO NOT run against a production database.
-- Internal status codes intentionally remain English to preserve Java enum mappings.
SET FOREIGN_KEY_CHECKS=0;
INSERT INTO `jobs` (`id`, `title`, `category`, `description`, `required_skills`, `budget`, `price_type`, `budget_type`, `deadline`, `estimated_duration_days`, `featured`, `verified`, `work_type`, `experience_level`, `escrow_funded`, `acceptance_criteria`, `status`, `proposals_count`, `customer_id`, `created_at`, `updated_at`, `attachment_key`, `attachment_original_name`) SELECT `id`+1000, CONCAT(`title`,' [B]'), `category`, `description`, `required_skills`, `budget`, `price_type`, `budget_type`, `deadline`, `estimated_duration_days`, `featured`, `verified`, `work_type`, `experience_level`, `escrow_funded`, `acceptance_criteria`, `status`, `proposals_count`, 20, `created_at`, `updated_at`, `attachment_key`, `attachment_original_name` FROM `jobs` WHERE `id`=60 OR `id` BETWEEN 385 AND 396;
INSERT INTO `job_applications` (`id`, `job_id`, `freelancer_id`, `proposal`, `proposed_price`, `estimated_days`, `portfolio_link`, `status`, `created_at`, `updated_at`) SELECT `id`+1000, `job_id`+1000, 27, `proposal`, `proposed_price`, `estimated_days`, `portfolio_link`, `status`, `created_at`, `updated_at` FROM `job_applications` WHERE `id`=13 OR `id` BETWEEN 23 AND 34;
INSERT INTO `projects` (`id`, `application_id`, `customer_id`, `freelancer_id`, `order_number`, `service_title`, `total_price`, `platform_fee`, `freelancer_payout_amount`, `deadline`, `revision_count`, `max_revisions_allowed`, `auto_accept_days_remaining`, `agreement_terms`, `agreement_version`, `sow_requirements`, `notes`, `payment_method`, `vnpay_transaction_id`, `status`, `cancellation_reason`, `credit_awarded`, `created_at`, `updated_at`, `completed_at`, `customer_signed_at`, `freelancer_signed_at`) SELECT `id`+1000, `application_id`+1000, 20, 27, CONCAT('TM-TEST-B-', `id`), CONCAT(`service_title`,' [B]'), `total_price`, `platform_fee`, `freelancer_payout_amount`, `deadline`, `revision_count`, `max_revisions_allowed`, `auto_accept_days_remaining`, `agreement_terms`, `agreement_version`, `sow_requirements`, `notes`, `payment_method`, `vnpay_transaction_id`, `status`, `cancellation_reason`, `credit_awarded`, `created_at`, `updated_at`, `completed_at`, `customer_signed_at`, `freelancer_signed_at` FROM `projects` WHERE `id` BETWEEN 10 AND 17;
INSERT INTO `quote_revisions` (`id`, `amount`, `created_at`, `estimated_days`, `resolved_at`, `revision_policy`, `scope`, `status`, `version`, `application_id`, `author_id`, `rejection_reason`, `negotiation_note`) SELECT `id`+1000, `amount`, `created_at`, `estimated_days`, `resolved_at`, `revision_policy`, `scope`, `status`, `version`, `application_id`+1000, IF(`author_id` IN (32,33,20),20,27), `rejection_reason`, `negotiation_note` FROM `quote_revisions` WHERE `application_id`=13 OR `application_id` BETWEEN 23 AND 34;
INSERT INTO `agreement_revisions` (`id`, `amount`, `created_at`, `customer_signed_at`, `estimated_days`, `freelancer_signed_at`, `scope_of_work`, `terms`, `version`, `project_id`) SELECT `id`+1000, `amount`, `created_at`, `customer_signed_at`, `estimated_days`, `freelancer_signed_at`, `scope_of_work`, `terms`, `version`, `project_id`+1000 FROM `agreement_revisions` WHERE `project_id` BETWEEN 10 AND 17;
INSERT INTO `milestones` (`id`, `project_id`, `title`, `description`, `amount`, `deadline`, `status`, `revision_note`, `created_at`, `completed_at`, `progress_percent`) SELECT `id`+1000, `project_id`+1000, CONCAT(`title`,' [B]'), `description`, `amount`, `deadline`, `status`, `revision_note`, `created_at`, `completed_at`, `progress_percent` FROM `milestones` WHERE `project_id` BETWEEN 10 AND 17;
INSERT INTO `payments` (`id`, `project_id`, `milestone_id`, `payer_id`, `amount`, `method`, `status`, `transaction_code`, `created_at`, `paid_at`, `disbursed_at`) SELECT `id`+1000, `project_id`+1000, IF(`milestone_id` IS NULL,NULL,`milestone_id`+1000), 20, `amount`, `method`, `status`, CONCAT(`transaction_code`,'-B'), `created_at`, `paid_at`, `disbursed_at` FROM `payments` WHERE `project_id` BETWEEN 10 AND 17;
INSERT INTO `progress_updates` (`id`, `created_at`, `evidence_url`, `note`, `percent`, `author_id`, `milestone_id`, `project_id`) SELECT `id`+1000, `created_at`, `evidence_url`, `note`, `percent`, 27, IF(`milestone_id` IS NULL,NULL,`milestone_id`+1000), `project_id`+1000 FROM `progress_updates` WHERE `project_id` BETWEEN 10 AND 17;
INSERT INTO `project_deliveries` (`id`, `project_id`, `milestone_id`, `file_name`, `file_url`, `file_size`, `demo_url`, `note`, `submitted_at`, `created_at`, `external_url`, `storage_key`, `version`, `submitter_id`) SELECT `id`+1000, `project_id`+1000, IF(`milestone_id` IS NULL,NULL,`milestone_id`+1000), `file_name`, `file_url`, `file_size`, `demo_url`, `note`, `submitted_at`, `created_at`, `external_url`, `storage_key`, `version`, 27 FROM `project_deliveries` WHERE `project_id` BETWEEN 10 AND 17;
INSERT INTO `disputes` (`id`, `project_id`, `order_number`, `opened_by`, `reason`, `evidence_notes`, `status`, `refund_employer_amount`, `payout_freelancer_amount`, `admin_resolution_note`, `resolved_by`, `created_at`, `resolved_at`) SELECT `id`+1000, 1016, `order_number`, 20, `reason`, `evidence_notes`, `status`, `refund_employer_amount`, `payout_freelancer_amount`, `admin_resolution_note`, IF(`resolved_by` IS NULL,NULL,1), `created_at`, `resolved_at` FROM `disputes` WHERE `project_id`=16;
INSERT INTO `wallet_withdrawals` (`id`, `freelancer_id`, `bank_id`, `amount`, `status`, `reason`, `created_at`, `updated_at`) SELECT `id`+1000, 27, 1, `amount`, `status`, `reason`, `created_at`, `updated_at` FROM `wallet_withdrawals` WHERE `id` BETWEEN 1 AND 5;
INSERT INTO `withdrawal_requests` (`id`, `user_id`, `bank_name`, `bank_code`, `account_number`, `account_holder`, `amount`, `fee`, `net_amount`, `status`, `admin_note`, `transaction_code`, `created_at`, `processed_at`) SELECT `id`+1000, 27, `bank_name`, `bank_code`, `account_number`, `account_holder`, `amount`, `fee`, `net_amount`, `status`, `admin_note`, CONCAT(`transaction_code`,'-B'), `created_at`, `processed_at` FROM `withdrawal_requests` WHERE `id` IN (1,2);
INSERT INTO `wallet_credits` (`id`, `freelancer_id`, `payment_id`, `gross`, `fee`, `net`, `created_at`) SELECT `id`+1000, 27, `payment_id`+1000, `gross`, `fee`, `net`, `created_at` FROM `wallet_credits` WHERE `id` IN (5,6);
DELETE FROM `withdrawal_requests` WHERE NOT (`id` IN (1,2,1001,1002));
DELETE FROM `wallet_withdrawals` WHERE NOT (`id` BETWEEN 1 AND 5 OR `id` BETWEEN 1001 AND 1005);
DELETE FROM `wallet_credits` WHERE NOT (`id` IN (5,6,1005,1006));
DELETE FROM `wallet_banks` WHERE NOT (`id`=1);
DELETE FROM `vnpay_transactions`;
DELETE FROM `verification_tokens`;
DELETE FROM `users` WHERE NOT (`id` IN (1,20,27));
DELETE FROM `user_bank_accounts`;
DELETE FROM `tm_gd1_delivery_submitter_repair_log`;
DELETE FROM `talent_reviews`;
DELETE FROM `talent_reports`;
DELETE FROM `saved_jobs`;
DELETE FROM `quote_revisions` WHERE NOT (`application_id` IN (13,23,24,25,26,27,28,29,30,31,32,33,34,1013,1023,1024,1025,1026,1027,1028,1029,1030,1031,1032,1033,1034));
DELETE FROM `projects` WHERE NOT (`id` BETWEEN 10 AND 17 OR `id` BETWEEN 1010 AND 1017);
DELETE FROM `project_deliveries` WHERE NOT (`project_id` BETWEEN 10 AND 17 OR `project_id` BETWEEN 1010 AND 1017);
DELETE FROM `progress_updates` WHERE NOT (`project_id` BETWEEN 10 AND 17 OR `project_id` BETWEEN 1010 AND 1017);
DELETE FROM `pending_registrations`;
DELETE FROM `payments` WHERE NOT (`project_id` BETWEEN 10 AND 17 OR `project_id` BETWEEN 1010 AND 1017);
DELETE FROM `password_reset_tokens`;
DELETE FROM `notifications`;
DELETE FROM `milestones` WHERE NOT (`project_id` BETWEEN 10 AND 17 OR `project_id` BETWEEN 1010 AND 1017);
DELETE FROM `jobs` WHERE NOT (`id` IN (60,385,386,387,388,389,390,391,392,393,394,395,396,1060,1385,1386,1387,1388,1389,1390,1391,1392,1393,1394,1395,1396));
DELETE FROM `job_questions`;
DELETE FROM `job_invitations`;
DELETE FROM `job_applications` WHERE NOT (`id` IN (13,23,24,25,26,27,28,29,30,31,32,33,34,1013,1023,1024,1025,1026,1027,1028,1029,1030,1031,1032,1033,1034));
DELETE FROM `freelancer_portfolios`;
DELETE FROM `disputes` WHERE NOT (`project_id` IN (16,1016));
DELETE FROM `customer_saved_freelancers`;
DELETE FROM `conversation_messages`;
DELETE FROM `company_profile_assets`;
DELETE FROM `community_votes`;
DELETE FROM `community_questions`;
DELETE FROM `community_bookmarks`;
DELETE FROM `community_answers`;
DELETE FROM `agreement_revisions` WHERE NOT (`project_id` BETWEEN 10 AND 17 OR `project_id` BETWEEN 1010 AND 1017);
DELETE FROM `agreement_change_requests`;
DELETE FROM `admin_audit_logs`;
UPDATE `jobs` SET `customer_id`=20;
UPDATE `job_applications` SET `freelancer_id`=27;
UPDATE `projects` SET `customer_id`=20, `freelancer_id`=27;
UPDATE `quote_revisions` SET `author_id`=IF(`author_id` IN (32,33,20),20,27);
UPDATE `payments` SET `payer_id`=20;
UPDATE `progress_updates` SET `author_id`=27;
UPDATE `project_deliveries` SET `submitter_id`=27;
UPDATE `disputes` SET `opened_by`=20, `resolved_by`=IF(`resolved_by` IS NULL,NULL,1);
UPDATE `wallet_banks` SET `freelancer_id`=27;
UPDATE `wallet_withdrawals` SET `freelancer_id`=27, `bank_id`=1;
UPDATE `wallet_credits` SET `freelancer_id`=27;
UPDATE `withdrawal_requests` SET `user_id`=27;
UPDATE `users` SET `password`=(SELECT password_hash FROM (SELECT `password` AS password_hash FROM `users` WHERE id=1) p), `enabled`=b'1', `locked`=b'0' WHERE `id` IN (1,20,27);
UPDATE `users` SET `full_name`='Client kiểm thử TalentMatch',`company_name`='Doanh nghiệp kiểm thử TalentMatch',`balance`=1000000000.00 WHERE `id`=20;
UPDATE `users` SET `full_name`='Freelancer kiểm thử TalentMatch' WHERE `id`=27;
SET FOREIGN_KEY_CHECKS=1;

-- Control queries
SELECT role, COUNT(*) AS accounts FROM users GROUP BY role;
SELECT 'Jobs' AS category, COUNT(*) AS total FROM jobs
UNION ALL SELECT 'Applications', COUNT(*) FROM job_applications
UNION ALL SELECT 'Projects', COUNT(*) FROM projects
UNION ALL SELECT 'Milestones', COUNT(*) FROM milestones
UNION ALL SELECT 'Payments', COUNT(*) FROM payments
UNION ALL SELECT 'Quotes', COUNT(*) FROM quote_revisions;
