-- Step 3: Sample data. Run AFTER the other two scripts.
-- Login users (with BCrypt hashes) are created by the Java class SeedData.
USE fund_flow;

INSERT INTO bank_branch (branch_name, location) VALUES ('Main Branch', 'Haldwani, Uttarakhand');

INSERT INTO employee (name, designation, phone, email, branch_id, supervisor_id) VALUES
 ('Rajesh Verma', 'Branch Manager', '9000000001', 'rajesh@bank.com', 1, NULL),
 ('Neha Joshi',   'Teller',         '9000000002', 'neha@bank.com',   1, 1);

INSERT INTO customer (name, email, phone, address, date_of_birth) VALUES
 ('Alice Sharma', 'alice@mail.com', '9111111111', 'Haldwani', '1998-04-12'),
 ('Bob Singh',    'bob@mail.com',   '9222222222', 'Nainital', '1995-09-30'),
 ('Carol Rawat',  'carol@mail.com', '9333333333', 'Kathgodam','2000-01-22');

-- Large balances so the 100-transfer concurrency test does not run dry
INSERT INTO account (account_no, customer_id, type, balance) VALUES
 ('ACC1001', 1, 'SAVINGS', 100000.00),
 ('ACC1002', 1, 'CURRENT',  50000.00),
 ('ACC1003', 2, 'SAVINGS', 100000.00),
 ('ACC1004', 2, 'CURRENT',  75000.00),
 ('ACC1005', 3, 'SAVINGS', 100000.00),
 ('ACC1006', 3, 'CURRENT',  25000.00);
