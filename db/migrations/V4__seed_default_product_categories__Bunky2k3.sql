-- Seeds a starter set of product categories so the product catalog
-- (ProductController, added alongside this migration) has somewhere to
-- attach products to -- productcategories.category_id is a NOT NULL FK
-- on products, so without at least one row here the "add product" form
-- has nothing to offer in its category dropdown.

INSERT INTO schema_migrations (version) VALUES ('V4__seed_default_product_categories__Bunky2k3');

INSERT INTO productcategories (category_name, parent_category_id, display_order) VALUES
('Máy POS', NULL, 1),
('Máy in hóa đơn', NULL, 2),
('Đầu đọc thẻ', NULL, 3),
('Phụ kiện POS', NULL, 4),
('Phần mềm POS', NULL, 5);
