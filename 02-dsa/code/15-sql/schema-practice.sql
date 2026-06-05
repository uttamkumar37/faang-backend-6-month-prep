-- SQL practice schema for backend interview drills.
-- Tested syntax target: PostgreSQL. Adapt identity and timestamp syntax if needed.

DROP TABLE IF EXISTS payment_events;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS support_tickets;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    category VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('CREATED', 'PAID', 'CANCELLED', 'SHIPPED')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_payment_id)
);

CREATE TABLE payment_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(120) NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_event_id)
);

CREATE TABLE support_tickets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'PENDING', 'CLOSED')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_tickets_status_created ON support_tickets(status, created_at DESC, id DESC);

INSERT INTO users(email, name) VALUES
('ana@example.com', 'Ana'),
('ben@example.com', 'Ben'),
('chi@example.com', 'Chi'),
('dev@example.com', 'Dev');

INSERT INTO products(sku, category, name, price) VALUES
('BOOK-1', 'books', 'Distributed Systems Notes', 45.00),
('JAVA-21', 'books', 'Modern Java', 55.00),
('SSD-1T', 'hardware', 'SSD 1TB', 120.00),
('KB-MECH', 'hardware', 'Mechanical Keyboard', 80.00);

INSERT INTO orders(user_id, status, created_at) VALUES
(1, 'PAID', now() - interval '10 days'),
(1, 'SHIPPED', now() - interval '5 days'),
(2, 'PAID', now() - interval '3 days'),
(3, 'CREATED', now() - interval '1 day');

INSERT INTO order_items(order_id, product_id, quantity, unit_price) VALUES
(1, 1, 1, 45.00),
(1, 3, 1, 120.00),
(2, 2, 2, 55.00),
(3, 4, 1, 80.00);

INSERT INTO payments(order_id, provider, provider_payment_id, amount, status, created_at) VALUES
(1, 'stripe', 'pay_100', 165.00, 'SUCCESS', now() - interval '10 days'),
(2, 'stripe', 'pay_101', 110.00, 'SUCCESS', now() - interval '5 days'),
(3, 'razorpay', 'pay_200', 80.00, 'SUCCESS', now() - interval '3 days');

INSERT INTO payment_events(provider, provider_event_id, order_id, status) VALUES
('stripe', 'evt_100', 1, 'SUCCESS'),
('stripe', 'evt_101', 2, 'SUCCESS'),
('razorpay', 'evt_200', 3, 'SUCCESS');

INSERT INTO support_tickets(user_id, priority, status, created_at) VALUES
(1, 'HIGH', 'OPEN', now() - interval '2 hours'),
(2, 'MEDIUM', 'PENDING', now() - interval '1 day'),
(3, 'LOW', 'CLOSED', now() - interval '8 days');
