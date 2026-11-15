-- RUN ONCE ONLY (skip if Hibernate ddl-auto=update already created the fields).
-- Back up talentmatch_pro before executing.
USE talentmatch_pro;
ALTER TABLE payments
  ADD COLUMN accepted_at DATETIME(6) NULL,
  ADD COLUMN scheduled_disbursement_at DATETIME(6) NULL,
  ADD COLUMN settlement_fee_percent DECIMAL(5,2) NULL;
-- Do not backfill historical DISBURSED/PAID records without trustworthy acceptance dates.
