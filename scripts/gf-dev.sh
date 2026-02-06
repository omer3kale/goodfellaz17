#!/bin/bash
set -e

echo "🚀 GoodFellaz17 Dev Stack"
echo "========================="

# Start PostgreSQL
echo "📦 Starting PostgreSQL..."
docker compose -f docker-compose.test-db.yml up -d
sleep 3

# Wait for healthy
echo "⏳ Waiting for database..."
until docker compose -f docker-compose.test-db.yml ps | grep -q "healthy"; do
    sleep 1
done
echo "✅ Database ready!"

# Seed test data
echo "🌱 Seeding test data..."
psql "postgresql://testuser:testpass@localhost:55432/goodfellaz17_test" -c "
-- Services (if not exists)
INSERT INTO services (service_id, name, category, rate, min_quantity, max_quantity, drip_feed, retention_days, description, delivery_strategy)
SELECT 1, 'Spotify Plays - Global', 'plays', 2.50, 100, 1000000, true, 30, 'High quality global plays', 'drip'
WHERE NOT EXISTS (SELECT 1 FROM services WHERE service_id = 1);

INSERT INTO services (service_id, name, category, rate, min_quantity, max_quantity, drip_feed, retention_days, description, delivery_strategy)
SELECT 2, 'Playlist Additions', 'playlist', 1.80, 50, 10000, false, 60, 'Real playlist placements', 'instant'
WHERE NOT EXISTS (SELECT 1 FROM services WHERE service_id = 2);

-- Sample orders (25 completed, 5 processing)
INSERT INTO orders (order_id, service_id, status, charged_count, quantity, charged, created_at, updated_at)
SELECT
  gen_random_uuid(),
  1,
  'Completed',
  (5000 + floor(random() * 10000))::int,
  (5000 + floor(random() * 10000))::int,
  (5000 + floor(random() * 10000))::int * 2.50 / 1000,
  NOW() - (random() * interval '30 days'),
  NOW()
FROM generate_series(1, 25)
WHERE NOT EXISTS (SELECT 1 FROM orders LIMIT 1);

INSERT INTO orders (order_id, service_id, status, charged_count, quantity, charged, created_at, updated_at)
SELECT
  gen_random_uuid(),
  2,
  'Processing',
  (1000 + floor(random() * 3000))::int,
  (5000 + floor(random() * 5000))::int,
  (1000 + floor(random() * 3000))::int * 1.80 / 1000,
  NOW() - (random() * interval '1 day'),
  NOW()
FROM generate_series(1, 5)
WHERE (SELECT COUNT(*) FROM orders WHERE status = 'Processing') < 5;
" 2>/dev/null || echo "⚠️  Seed skipped (data may already exist)"

echo ""
echo "✅ Dev environment ready!"
echo ""
echo "📊 Endpoints:"
echo "  Dashboard:  http://localhost:8080/api/dashboard"
echo "  Admin:      http://localhost:8080/api/admin/stats"
echo "  Capacity:   http://localhost:8080/api/dashboard/capacity"
echo "  Live:       http://localhost:8080/api/dashboard/live"
echo "  Revenue:    http://localhost:8080/api/admin/revenue"
echo ""
echo "🧪 Quick test after startup:"
echo "  curl http://localhost:8080/api/dashboard | jq"
echo ""
echo "🚀 Starting Spring Boot..."
echo ""

mvn spring-boot:run -Dspring.profiles.active=local
