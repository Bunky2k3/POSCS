-- Products can now have multiple uploaded images and multiple catalogue
-- files (chosen from disk in the UI) instead of a single pasted-in URL
-- each. Replaces products.image_url/catalogue_url with one-to-many
-- productimages/productcatalogues tables.

INSERT INTO schema_migrations (version) VALUES ('V6__product_multi_image_catalogue__Bunky2k3');

CREATE TABLE productimages (
  image_id INT NOT NULL AUTO_INCREMENT,
  product_id INT NOT NULL,
  image_url VARCHAR(255) NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (image_id),
  KEY product_id (product_id),
  CONSTRAINT productimages_ibfk_1 FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE productcatalogues (
  catalogue_id INT NOT NULL AUTO_INCREMENT,
  product_id INT NOT NULL,
  catalogue_url VARCHAR(255) NOT NULL,
  file_name VARCHAR(255) DEFAULT NULL,
  display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (catalogue_id),
  KEY product_id (product_id),
  CONSTRAINT productcatalogues_ibfk_1 FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Carry forward whatever single image_url/catalogue_url each product
-- already had (e.g. the postef.com.vn seed data from V5) before the
-- columns disappear.
INSERT INTO productimages (product_id, image_url, display_order)
SELECT product_id, image_url, 0 FROM products WHERE image_url IS NOT NULL;

INSERT INTO productcatalogues (product_id, catalogue_url, display_order)
SELECT product_id, catalogue_url, 0 FROM products WHERE catalogue_url IS NOT NULL;

ALTER TABLE products DROP COLUMN image_url;
ALTER TABLE products DROP COLUMN catalogue_url;
