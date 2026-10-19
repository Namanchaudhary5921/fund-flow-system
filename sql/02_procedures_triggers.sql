-- =====================================================================
-- Step 2: Stored procedures + trigger
-- Run:  mysql -u root -p < sql/02_procedures_triggers.sql
-- DBMS concepts shown: ACID transaction, SELECT ... FOR UPDATE (row lock),
-- ROLLBACK on error, trigger-based audit logging.
-- =====================================================================
USE fund_flow;
DELIMITER $$

-- ---------------------------------------------------------------- DEPOSIT
DROP PROCEDURE IF EXISTS sp_deposit$$
CREATE PROCEDURE sp_deposit(
    IN  p_account VARCHAR(12),
    IN  p_amount  DECIMAL(15,2),
    OUT p_status  VARCHAR(10),
    OUT p_message VARCHAR(200))
main: BEGIN
    DECLARE v_acc_status VARCHAR(10) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;                                   -- Atomicity: undo everything
        SET p_status = 'FAILED';
        SET p_message = 'Database error - transaction rolled back';
    END;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_status = 'FAILED'; SET p_message = 'Amount must be positive';
        LEAVE main;
    END IF;

    START TRANSACTION;
    SELECT status INTO v_acc_status FROM account WHERE account_no = p_account FOR UPDATE;

    IF v_acc_status IS NULL THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Account not found';
        LEAVE main;
    END IF;
    IF v_acc_status <> 'ACTIVE' THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = CONCAT('Account is ', v_acc_status);
        LEAVE main;
    END IF;

    UPDATE account SET balance = balance + p_amount WHERE account_no = p_account;
    INSERT INTO transactions (type, amount, description, to_account)
    VALUES ('DEPOSIT', p_amount, 'Deposit', p_account);
    COMMIT;

    SET p_status = 'SUCCESS'; SET p_message = 'Deposit successful';
END main$$

-- --------------------------------------------------------------- WITHDRAW
DROP PROCEDURE IF EXISTS sp_withdraw$$
CREATE PROCEDURE sp_withdraw(
    IN  p_account VARCHAR(12),
    IN  p_amount  DECIMAL(15,2),
    OUT p_status  VARCHAR(10),
    OUT p_message VARCHAR(200))
main: BEGIN
    DECLARE v_acc_status VARCHAR(10) DEFAULT NULL;
    DECLARE v_balance DECIMAL(15,2) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_status = 'FAILED';
        SET p_message = 'Database error - transaction rolled back';
    END;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_status = 'FAILED'; SET p_message = 'Amount must be positive';
        LEAVE main;
    END IF;

    START TRANSACTION;
    SELECT balance, status INTO v_balance, v_acc_status
      FROM account WHERE account_no = p_account FOR UPDATE;

    IF v_acc_status IS NULL THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Account not found';
        LEAVE main;
    END IF;
    IF v_acc_status <> 'ACTIVE' THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = CONCAT('Account is ', v_acc_status);
        LEAVE main;
    END IF;
    IF v_balance < p_amount THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Insufficient balance';
        LEAVE main;
    END IF;

    UPDATE account SET balance = balance - p_amount WHERE account_no = p_account;
    INSERT INTO transactions (type, amount, description, from_account)
    VALUES ('WITHDRAW', p_amount, 'Withdrawal', p_account);
    COMMIT;

    SET p_status = 'SUCCESS'; SET p_message = 'Withdrawal successful';
END main$$

-- --------------------------------------------------------- FUND TRANSFER
-- Deadlock prevention at DB level: the account with the SMALLER account_no
-- is always locked first, whatever the direction of the transfer.
DROP PROCEDURE IF EXISTS sp_transfer_funds$$
CREATE PROCEDURE sp_transfer_funds(
    IN  p_from   VARCHAR(12),
    IN  p_to     VARCHAR(12),
    IN  p_amount DECIMAL(15,2),
    OUT p_status  VARCHAR(10),
    OUT p_message VARCHAR(200))
main: BEGIN
    DECLARE v_from_bal    DECIMAL(15,2) DEFAULT NULL;
    DECLARE v_from_status VARCHAR(10)   DEFAULT NULL;
    DECLARE v_to_status   VARCHAR(10)   DEFAULT NULL;
    DECLARE v_trans_id    BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_status = 'FAILED';
        SET p_message = 'Database error - transaction rolled back';
    END;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        SET p_status = 'FAILED'; SET p_message = 'Amount must be positive';
        LEAVE main;
    END IF;
    IF p_from = p_to THEN
        SET p_status = 'FAILED'; SET p_message = 'Cannot transfer to the same account';
        LEAVE main;
    END IF;

    START TRANSACTION;

    IF p_from < p_to THEN
        SELECT balance, status INTO v_from_bal, v_from_status FROM account WHERE account_no = p_from FOR UPDATE;
        SELECT status INTO v_to_status FROM account WHERE account_no = p_to FOR UPDATE;
    ELSE
        SELECT status INTO v_to_status FROM account WHERE account_no = p_to FOR UPDATE;
        SELECT balance, status INTO v_from_bal, v_from_status FROM account WHERE account_no = p_from FOR UPDATE;
    END IF;

    IF v_from_status IS NULL THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Source account not found';
        LEAVE main;
    END IF;
    IF v_to_status IS NULL THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Destination account not found';
        LEAVE main;
    END IF;
    IF v_from_status <> 'ACTIVE' OR v_to_status <> 'ACTIVE' THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Both accounts must be ACTIVE';
        LEAVE main;
    END IF;
    IF v_from_bal < p_amount THEN
        ROLLBACK; SET p_status = 'FAILED'; SET p_message = 'Insufficient balance';
        LEAVE main;
    END IF;

    UPDATE account SET balance = balance - p_amount WHERE account_no = p_from;
    UPDATE account SET balance = balance + p_amount WHERE account_no = p_to;

    INSERT INTO transactions (type, amount, description, from_account, to_account)
    VALUES ('TRANSFER', p_amount, CONCAT('Transfer ', p_from, ' -> ', p_to), p_from, p_to);
    SET v_trans_id = LAST_INSERT_ID();

    INSERT INTO fund_transfer (trans_id, from_account, to_account, amount)
    VALUES (v_trans_id, p_from, p_to, p_amount);

    COMMIT;
    SET p_status = 'SUCCESS'; SET p_message = 'Transfer successful';
END main$$

-- ---------------------------------------------------------------- TRIGGER
-- Every balance change is audited automatically, no matter who changed it.
DROP TRIGGER IF EXISTS trg_account_balance_audit$$
CREATE TRIGGER trg_account_balance_audit
AFTER UPDATE ON account
FOR EACH ROW
BEGIN
    IF OLD.balance <> NEW.balance THEN
        INSERT INTO audit_log (account_no, action, old_balance, new_balance)
        VALUES (NEW.account_no,
                IF(NEW.balance > OLD.balance, 'CREDIT', 'DEBIT'),
                OLD.balance, NEW.balance);
    END IF;
END$$

DELIMITER ;
