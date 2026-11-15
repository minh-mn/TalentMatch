-- Tao mot hop dong demo co the thanh toan bang VNPay Sandbox.
-- Chi dung cho database demo/kiem thu, khong chay tren production.
-- Yeu cau: da chay ung dung it nhat mot lan de Hibernate tao/cap nhat schema.
-- Tai khoan demo can co san:
--   Client:     client@talentmatch.vn
--   Freelancer: freelancer@talentmatch.vn
-- Script khong tao giao dich payments; Client se tu bat dau checkout VNPay.
-- Co the chay lai an toan: cac ban ghi duoc nhan dien boi title/order number/version.

START TRANSACTION;

SET @client_id := (
    SELECT id FROM users
    WHERE LOWER(email) = 'client@talentmatch.vn' AND role = 'CUSTOMER'
    LIMIT 1
);
SET @freelancer_id := (
    SELECT id FROM users
    WHERE LOWER(email) = 'freelancer@talentmatch.vn' AND role = 'FREELANCER'
    LIMIT 1
);

SELECT @client_id AS demo_client_id, @freelancer_id AS demo_freelancer_id;

INSERT INTO jobs (
    title, category, description, required_skills, budget, price_type,
    budget_type, deadline, estimated_duration_days, featured, verified,
    work_type, experience_level, escrow_funded, acceptance_criteria,
    status, proposals_count, customer_id, created_at, updated_at
)
SELECT
    '[DEMO-VNPAY-01] Xay dung landing page thuong mai dien tu',
    'Lập trình & CNTT',
    'Du an mau de kiem thu luong hop dong va thanh toan VNPay Sandbox.',
    'HTML, CSS, JavaScript, Responsive',
    500000, 'FIXED', 'FIXED', DATE_ADD(CURDATE(), INTERVAL 14 DAY),
    14, FALSE, TRUE, 'REMOTE', 'MIDDLE', FALSE,
    'Hoan thanh landing page responsive va ban giao ma nguon demo.',
    'CLOSED', 1, @client_id, NOW(), NOW()
FROM DUAL
WHERE @client_id IS NOT NULL
  AND @freelancer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM jobs
      WHERE title = '[DEMO-VNPAY-01] Xay dung landing page thuong mai dien tu'
        AND customer_id = @client_id
  );

SET @job_id := (
    SELECT id FROM jobs
    WHERE title = '[DEMO-VNPAY-01] Xay dung landing page thuong mai dien tu'
      AND customer_id = @client_id
    ORDER BY id DESC LIMIT 1
);

INSERT INTO job_applications (
    job_id, freelancer_id, proposal, proposed_price, estimated_days,
    portfolio_link, status, created_at, updated_at
)
SELECT @job_id, @freelancer_id,
       'Toi se thiet ke va xay dung landing page responsive, sau do ban giao source code va huong dan chay.',
       500000, 10, 'https://portfolio.example.test/landing-page',
       'ACCEPTED', NOW(), NOW()
FROM DUAL
WHERE @job_id IS NOT NULL
  AND @freelancer_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM job_applications
      WHERE job_id = @job_id AND freelancer_id = @freelancer_id
  );

SET @application_id := (
    SELECT id FROM job_applications
    WHERE job_id = @job_id AND freelancer_id = @freelancer_id
    LIMIT 1
);

INSERT INTO quote_revisions (
    application_id, author_id, version, amount, estimated_days, scope,
    revision_policy, status, created_at, resolved_at
)
SELECT @application_id, @client_id, 1, 500000, 10,
       'Xay dung landing page responsive, toi uu tren desktop va mobile; ban giao source code.',
       'Ho tro mot vong chinh sua nho trong pham vi da thong nhat.',
       'ACCEPTED', NOW(), NOW()
FROM DUAL
WHERE @application_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM quote_revisions
      WHERE application_id = @application_id AND version = 1
  );

INSERT INTO projects (
    application_id, customer_id, freelancer_id, order_number, service_title,
    total_price, platform_fee, freelancer_payout_amount, deadline,
    revision_count, max_revisions_allowed, auto_accept_days_remaining,
    agreement_terms, agreement_version, sow_requirements, notes,
    payment_method, vnpay_transaction_id, status, cancellation_reason,
    credit_awarded, created_at, updated_at, completed_at,
    customer_signed_at, freelancer_signed_at, payment_due_date
)
SELECT @application_id, @client_id, @freelancer_id, 'TM-DEMO-VNPAY-01',
       '[DEMO-VNPAY-01] Xay dung landing page thuong mai dien tu',
       500000, 50000, 450000, DATE_ADD(CURDATE(), INTERVAL 14 DAY),
       0, 1, 0,
       'Hop dong demo VNPay Sandbox. Hai ben dong y pham vi, gia tri va thoi han ghi trong ban ghi nay.',
       1,
       'Xay dung landing page responsive, toi uu tren desktop va mobile; ban giao source code.',
       'Du lieu kiem thu; khong phat sinh thanh toan that.',
       NULL, NULL, 'WAITING_PAYMENT', NULL, FALSE, NOW(), NOW(), NULL,
       NOW(), NOW(), DATE_ADD(CURDATE(), INTERVAL 7 DAY)
FROM DUAL
WHERE @application_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM projects
      WHERE application_id = @application_id
  );

SET @project_id := (
    SELECT id FROM projects WHERE application_id = @application_id LIMIT 1
);

INSERT INTO agreement_revisions (
    project_id, version, terms, scope_of_work, amount, estimated_days,
    created_at, customer_signed_at, freelancer_signed_at
)
SELECT @project_id, 1,
       'Hop dong demo VNPay Sandbox. Hai ben dong y pham vi, gia tri va thoi han ghi trong ban ghi nay.',
       'Xay dung landing page responsive, toi uu tren desktop va mobile; ban giao source code.',
       500000, 10, NOW(), NOW(), NOW()
FROM DUAL
WHERE @project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM agreement_revisions
      WHERE project_id = @project_id AND version = 1
  );

UPDATE jobs
SET status = 'CLOSED', updated_at = NOW()
WHERE id = @job_id;

COMMIT;

-- Kiem tra hop dong du dieu kien checkout (chua tao payment).
SELECT
    p.id AS project_id,
    j.title AS project_title,
    c.email AS client_email,
    f.email AS freelancer_email,
    p.total_price,
    p.status AS project_status,
    p.payment_due_date,
    a.version AS agreement_version,
    a.customer_signed_at,
    a.freelancer_signed_at,
    (SELECT COUNT(*) FROM payments pay WHERE pay.project_id = p.id) AS existing_payments
FROM projects p
JOIN job_applications ja ON ja.id = p.application_id
JOIN jobs j ON j.id = ja.job_id
JOIN users c ON c.id = p.customer_id
JOIN users f ON f.id = p.freelancer_id
JOIN agreement_revisions a ON a.project_id = p.id AND a.version = 1
WHERE p.id = @project_id;
