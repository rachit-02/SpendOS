-- The original CHECK compared against the database's CURRENT_DATE (UTC). For users in timezones ahead
-- of UTC (e.g. IST) "today" can be tomorrow in UTC, so allow one day of slack. The service layer
-- enforces "not in the future" in the user's own timezone.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS valid_date;
ALTER TABLE transactions ADD CONSTRAINT valid_date CHECK (transaction_date <= CURRENT_DATE + 1);

-- Remaining subcategories from PRODUCT_SPEC.md (Food and Transport were seeded in V1).
INSERT INTO subcategories (category_id, subcategory_name, icon_name, display_order)
SELECT c.id, s.name, s.icon, s.display_order
FROM categories c
JOIN (VALUES
    ('Shopping', 'Electronics', 'cpu', 1),
    ('Shopping', 'Clothing', 'shirt', 2),
    ('Shopping', 'General', 'shopping-bag', 3),
    ('Bills', 'Electricity', 'zap', 1),
    ('Bills', 'Internet', 'wifi', 2),
    ('Bills', 'Mobile', 'smartphone', 3),
    ('Bills', 'Rent', 'home', 4),
    ('Subscriptions', 'Streaming', 'tv', 1),
    ('Subscriptions', 'Software', 'app-window', 2),
    ('Subscriptions', 'Fitness', 'dumbbell', 3),
    ('Income', 'Salary', 'briefcase', 1),
    ('Income', 'Refunds', 'rotate-ccw', 2),
    ('Income', 'Interest', 'percent', 3)
) AS s(category_name, name, icon, display_order) ON s.category_name = c.category_name
ON CONFLICT (category_id, subcategory_name) DO NOTHING;
