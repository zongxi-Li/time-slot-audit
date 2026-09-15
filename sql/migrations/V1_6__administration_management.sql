-- Administration domain query indexes.
-- Prerequisite: schema baseline containing reservation, approval_record and operation_log.

ALTER TABLE approval_record
    ADD KEY idx_approval_created_at (created_at);

ALTER TABLE operation_log
    ADD KEY idx_operation_business_created (business_type, created_at),
    ADD KEY idx_operation_user_created (user_id, created_at);

ALTER TABLE reservation
    ADD KEY idx_reservation_status_period (status, start_time, end_time);
