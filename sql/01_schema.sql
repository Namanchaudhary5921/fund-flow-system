-- =====================================================================
-- Fund Flow Management System  -  Step 1: Schema
-- Run:  mysql -u root -p < sql/01_schema.sql
-- (MySQL 8.0.16+ is required so that CHECK constraints are enforced)
-- =====================================================================
DROP DATABASE IF EXISTS fund_flow;
CREATE DATABASE fund_flow;
USE fund_flow;

CREATE TABLE bank_branch (
    branch_id   INT AUTO_INCREMENT PRIMARY KEY,
    branch_name VARCHAR(80)  NOT NULL,
    location    VARCHAR(120) NOT NULL
);

CREATE TABLE employee (
    emp_id        INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80) NOT NULL,
    designation   VARCHAR(60),
    phone         VARCHAR(15),
    email         VARCHAR(100) UNIQUE,
    branch_id     INT NOT NULL,
    supervisor_id INT NULL,                       -- "Supervises" relationship (1:N, self reference)
    FOREIGN KEY (branch_id)     REFERENCES bank_branch(branch_id),
    FOREIGN KEY (supervisor_id) REFERENCES employee(emp_id)
);

CREATE TABLE customer (
    customer_id   INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80) NOT NULL,
    email         VARCHAR(100) UNIQUE,
    phone         VARCHAR(15),
    address       VARCHAR(200),
    date_of_birth DATE
);

-- Login table: passwords are stored as BCrypt hashes (never plain text)
CREATE TABLE user_login (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          ENUM('CUSTOMER','ADMIN') NOT NULL,
    customer_id   INT NULL,
    emp_id        INT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (emp_id)      REFERENCES employee(emp_id)
);

CREATE TABLE account (
    account_no  VARCHAR(12) PRIMARY KEY,           -- PK is automatically indexed
    customer_id INT NOT NULL,
    type        ENUM('SAVINGS','CURRENT') NOT NULL,
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0,
    status      ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    opened_date DATE NOT NULL DEFAULT (CURRENT_DATE),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    INDEX idx_account_customer (customer_id)
);

-- "Transaction" is the ER entity; table is named "transactions" to avoid keyword clashes.
CREATE TABLE transactions (
    trans_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    trans_date   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    type         ENUM('DEPOSIT','WITHDRAW','TRANSFER') NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    status       ENUM('SUCCESS','FAILED') NOT NULL DEFAULT 'SUCCESS',
    description  VARCHAR(200),
    from_account VARCHAR(12) NULL,                 -- nullable (deposit has no source)
    to_account   VARCHAR(12) NULL,                 -- nullable (withdraw has no destination)
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    FOREIGN KEY (from_account) REFERENCES account(account_no),
    FOREIGN KEY (to_account)   REFERENCES account(account_no),
    INDEX idx_trans_date (trans_date),
    INDEX idx_trans_from (from_account),
    INDEX idx_trans_to   (to_account)
);

-- Fund Transfer is a specialisation of Transaction (1:1 with a TRANSFER row)
CREATE TABLE fund_transfer (
    transfer_id     INT AUTO_INCREMENT PRIMARY KEY,
    trans_id        BIGINT NOT NULL UNIQUE,
    from_account    VARCHAR(12) NOT NULL,
    to_account      VARCHAR(12) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    transfer_status ENUM('SUCCESS','FAILED') NOT NULL DEFAULT 'SUCCESS',
    FOREIGN KEY (trans_id)     REFERENCES transactions(trans_id),
    FOREIGN KEY (from_account) REFERENCES account(account_no),
    FOREIGN KEY (to_account)   REFERENCES account(account_no)
);

-- Filled automatically by a trigger (see 02_procedures_triggers.sql).
-- No FK on purpose: audit rows must survive even if other rows change.
CREATE TABLE audit_log (
    log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    trans_id    BIGINT NULL,
    account_no  VARCHAR(12) NOT NULL,
    action      VARCHAR(40) NOT NULL,
    old_balance DECIMAL(15,2),
    new_balance DECIMAL(15,2),
    result      VARCHAR(20) NOT NULL DEFAULT 'OK',
    log_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_audit_account (account_no),
    INDEX idx_audit_time (log_time)
);

-- Persisted copy of the producer-consumer queue (the live queue is in Java memory)
CREATE TABLE processing_request (
    request_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_type   ENUM('DEPOSIT','WITHDRAW','TRANSFER') NOT NULL,
    from_account   VARCHAR(12) NULL,
    to_account     VARCHAR(12) NULL,
    amount         DECIMAL(15,2) NOT NULL,
    priority       INT NOT NULL DEFAULT 5,          -- 1 = highest
    status         ENUM('PENDING','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    result_message VARCHAR(200),
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at   DATETIME(3) NULL,
    INDEX idx_req_status (status)
);
