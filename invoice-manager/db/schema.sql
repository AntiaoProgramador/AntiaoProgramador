CREATE DATABASE IF NOT EXISTS invoice_manager;
USE invoice_manager;

CREATE TABLE IF NOT EXISTS systems (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_number VARCHAR(60) NOT NULL UNIQUE,
    issuer VARCHAR(120) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    description VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoice_system_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'DISAGREED') NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(255) DEFAULT '',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_invoice_system (invoice_id, system_id),
    CONSTRAINT fk_status_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_status_system FOREIGN KEY (system_id) REFERENCES systems(id)
);

CREATE TABLE IF NOT EXISTS invoice_disagreements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    system_name VARCHAR(50) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    details VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_disagreement_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);

INSERT INTO systems (name)
VALUES ('ERP'), ('Financeiro'), ('Fiscal')
ON DUPLICATE KEY UPDATE name = VALUES(name);
