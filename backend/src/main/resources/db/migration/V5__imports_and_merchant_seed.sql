-- Import jobs: target account, file fingerprint (re-import prevention) and row total (progress).
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS total_rows INTEGER DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_import_jobs_user_hash ON import_jobs(user_id, file_hash);

-- Merchants can carry a default subcategory (e.g. Zomato -> Food / Food Delivery).
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS subcategory_id UUID REFERENCES subcategories(id);

-- Seed well-known merchants (verified) with their default category/subcategory.
INSERT INTO merchants (merchant_name, merchant_name_lower, category_id, subcategory_id, confidence_score, is_verified)
SELECT m.name, LOWER(m.name), c.id, s.id, 0.95, TRUE
FROM (VALUES
    ('Zomato', 'Food', 'Food Delivery'),
    ('Swiggy', 'Food', 'Food Delivery'),
    ('Domino''s', 'Food', 'Restaurants'),
    ('McDonald''s', 'Food', 'Restaurants'),
    ('Starbucks', 'Food', 'Restaurants'),
    ('KFC', 'Food', 'Restaurants'),
    ('BigBasket', 'Food', 'Groceries'),
    ('Blinkit', 'Food', 'Groceries'),
    ('Zepto', 'Food', 'Groceries'),
    ('DMart', 'Food', 'Groceries'),
    ('Uber', 'Transport', 'Cab'),
    ('Ola', 'Transport', 'Cab'),
    ('Rapido', 'Transport', 'Cab'),
    ('Indian Oil', 'Transport', 'Fuel'),
    ('Bharat Petroleum', 'Transport', 'Fuel'),
    ('HP Petrol', 'Transport', 'Fuel'),
    ('Metro Rail', 'Transport', 'Public Transport'),
    ('IRCTC', 'Travel', NULL),
    ('MakeMyTrip', 'Travel', NULL),
    ('IndiGo', 'Travel', NULL),
    ('Amazon', 'Shopping', 'General'),
    ('Flipkart', 'Shopping', 'General'),
    ('Myntra', 'Shopping', 'Clothing'),
    ('Croma', 'Shopping', 'Electronics'),
    ('Amazon Prime', 'Subscriptions', 'Streaming'),
    ('Netflix', 'Subscriptions', 'Streaming'),
    ('Spotify', 'Subscriptions', 'Streaming'),
    ('Disney+ Hotstar', 'Subscriptions', 'Streaming'),
    ('YouTube Premium', 'Subscriptions', 'Streaming'),
    ('Cult.fit', 'Subscriptions', 'Fitness'),
    ('Jio', 'Bills', 'Mobile'),
    ('Airtel', 'Bills', 'Mobile'),
    ('ACT Fibernet', 'Bills', 'Internet'),
    ('BESCOM', 'Bills', 'Electricity'),
    ('Tata Power', 'Bills', 'Electricity'),
    ('Apollo Pharmacy', 'Healthcare', NULL),
    ('PharmEasy', 'Healthcare', NULL),
    ('Practo', 'Healthcare', NULL),
    ('BookMyShow', 'Entertainment', NULL),
    ('PVR Cinemas', 'Entertainment', NULL),
    ('Udemy', 'Education', NULL),
    ('Coursera', 'Education', NULL)
) AS m(name, category, subcategory)
JOIN categories c ON c.category_name = m.category
LEFT JOIN subcategories s ON s.category_id = c.id AND s.subcategory_name = m.subcategory
ON CONFLICT (merchant_name_lower) DO UPDATE
    SET category_id = COALESCE(merchants.category_id, EXCLUDED.category_id),
        subcategory_id = COALESCE(merchants.subcategory_id, EXCLUDED.subcategory_id),
        is_verified = TRUE;

-- Normalization rules: raw statement text -> canonical merchant. Lower priority wins.
INSERT INTO merchant_normalization_rules (pattern, pattern_type, target_merchant_id, priority)
SELECT r.pattern, r.pattern_type, m.id, r.priority
FROM (VALUES
    ('zomato', 'contains', 'Zomato', 100),
    ('swiggy', 'contains', 'Swiggy', 100),
    ('domino', 'contains', 'Domino''s', 100),
    ('mcdonald', 'contains', 'McDonald''s', 100),
    ('starbucks', 'contains', 'Starbucks', 100),
    ('\bkfc\b', 'regex', 'KFC', 100),
    ('bigbasket', 'contains', 'BigBasket', 100),
    ('blinkit', 'contains', 'Blinkit', 100),
    ('grofers', 'contains', 'Blinkit', 100),
    ('zepto', 'contains', 'Zepto', 100),
    ('dmart', 'contains', 'DMart', 100),
    ('avenue supermarts', 'contains', 'DMart', 100),
    ('uber', 'contains', 'Uber', 100),
    ('olacabs', 'contains', 'Ola', 90),
    ('\bola\b', 'regex', 'Ola', 100),
    ('rapido', 'contains', 'Rapido', 100),
    ('indian oil', 'contains', 'Indian Oil', 100),
    ('iocl', 'contains', 'Indian Oil', 100),
    ('bharat petroleum', 'contains', 'Bharat Petroleum', 100),
    ('bpcl', 'contains', 'Bharat Petroleum', 100),
    ('hpcl', 'contains', 'HP Petrol', 100),
    ('hindustan petroleum', 'contains', 'HP Petrol', 100),
    ('metro rail', 'contains', 'Metro Rail', 100),
    ('dmrc', 'contains', 'Metro Rail', 100),
    ('bmrcl', 'contains', 'Metro Rail', 100),
    ('irctc', 'contains', 'IRCTC', 100),
    ('makemytrip', 'contains', 'MakeMyTrip', 100),
    ('indigo', 'contains', 'IndiGo', 100),
    ('amazon prime', 'contains', 'Amazon Prime', 10),
    ('primevideo', 'contains', 'Amazon Prime', 10),
    ('prime video', 'contains', 'Amazon Prime', 10),
    ('amazon', 'contains', 'Amazon', 100),
    ('amzn', 'contains', 'Amazon', 100),
    ('flipkart', 'contains', 'Flipkart', 100),
    ('myntra', 'contains', 'Myntra', 100),
    ('croma', 'contains', 'Croma', 100),
    ('netflix', 'contains', 'Netflix', 100),
    ('spotify', 'contains', 'Spotify', 100),
    ('hotstar', 'contains', 'Disney+ Hotstar', 100),
    ('youtube', 'contains', 'YouTube Premium', 100),
    ('cult.fit', 'contains', 'Cult.fit', 100),
    ('cultfit', 'contains', 'Cult.fit', 100),
    ('curefit', 'contains', 'Cult.fit', 100),
    ('\bjio\b', 'regex', 'Jio', 100),
    ('airtel', 'contains', 'Airtel', 100),
    ('act fibernet', 'contains', 'ACT Fibernet', 100),
    ('atria convergence', 'contains', 'ACT Fibernet', 100),
    ('bescom', 'contains', 'BESCOM', 100),
    ('tata power', 'contains', 'Tata Power', 100),
    ('apollo pharmacy', 'contains', 'Apollo Pharmacy', 100),
    ('pharmeasy', 'contains', 'PharmEasy', 100),
    ('practo', 'contains', 'Practo', 100),
    ('bookmyshow', 'contains', 'BookMyShow', 100),
    ('\bpvr\b', 'regex', 'PVR Cinemas', 100),
    ('udemy', 'contains', 'Udemy', 100),
    ('coursera', 'contains', 'Coursera', 100)
) AS r(pattern, pattern_type, merchant, priority)
JOIN merchants m ON m.merchant_name = r.merchant
WHERE NOT EXISTS (
    SELECT 1 FROM merchant_normalization_rules x
    WHERE x.pattern = r.pattern AND x.target_merchant_id = m.id
);
