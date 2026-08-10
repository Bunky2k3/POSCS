-- Adds the columns the customer JSPs (listcustomer/addnewcustomer/
-- updatecustomer/viewcustomerdetail) already expect but the schema
-- never had: customer_type, customer_group, account_owner_id,
-- website, join_date.

INSERT INTO schema_migrations (version) VALUES ('V1__add_customer_fields_to_enterprises__ndat2003');

ALTER TABLE enterprises
    ADD COLUMN customer_type VARCHAR(100) NOT NULL AFTER enterprise_name,
    ADD COLUMN customer_group VARCHAR(50) NOT NULL AFTER customer_type,
    ADD COLUMN website VARCHAR(255) NULL AFTER phone,
    ADD COLUMN account_owner_id INT NOT NULL AFTER address_id,
    ADD COLUMN join_date DATE NULL AFTER status,
    ADD CONSTRAINT fk_enterprises_account_owner FOREIGN KEY (account_owner_id) REFERENCES users (user_id);
