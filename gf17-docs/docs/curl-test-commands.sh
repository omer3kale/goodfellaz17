# =============================================================================
# GoodfellaZ17 API - Manual Testing with curl
# =============================================================================
#
# Prerequisites:
# 1. Start the application: mvn spring-boot:run
# 2. Database is seeded with test data
# 3. Set environment variables below
#
# Base URL (adjust for your environment):
BASE_URL="http://localhost:8080"

# Test API Key (replace with actual from database):
API_KEY="your-api-key-here"

# Test Service ID (replace with actual from database):
SERVICE_ID="your-service-uuid-here"

# =============================================================================
# CURL COMMAND 1: Get User Balance
# =============================================================================
# Check current balance before placing order
# Expected: 200 OK with balance, tier, email

curl -X GET "${BASE_URL}/api/v2/orders/balance" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Accept: application/json" | jq

# =============================================================================
# CURL COMMAND 2: Create Order (15k Spotify Plays)
# =============================================================================
# POST /api/v2/orders
# Expected: 201 Created with order details
# - €2.50/1k for CONSUMER tier = €37.50 total for 15k plays

curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "serviceId": "'"${SERVICE_ID}"'",
    "quantity": 15000,
    "targetUrl": "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
    "geoProfile": "WORLDWIDE",
    "speedMultiplier": 1.0
  }' | jq

# Save the returned order ID for subsequent commands
# ORDER_ID="returned-order-uuid"

# =============================================================================
# CURL COMMAND 3: Get Order by ID
# =============================================================================
# GET /api/v2/orders/{orderId}
# Expected: 200 OK with order details

ORDER_ID="your-order-uuid-here"

curl -X GET "${BASE_URL}/api/v2/orders/${ORDER_ID}" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Accept: application/json" | jq

# =============================================================================
# CURL COMMAND 4: List All Orders (with pagination)
# =============================================================================
# GET /api/v2/orders?page=0&size=10
# Expected: 200 OK with array of orders

curl -X GET "${BASE_URL}/api/v2/orders?page=0&size=10" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Accept: application/json" | jq

# =============================================================================
# CURL COMMAND 5: Verify Balance After Order
# =============================================================================
# Check balance was deducted
# Expected: Balance reduced by €37.50 (for 15k plays order)

curl -X GET "${BASE_URL}/api/v2/orders/balance" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Accept: application/json" | jq

# =============================================================================
# ERROR CASE TESTS
# =============================================================================

# Test 401 - No API Key
curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "'"${SERVICE_ID}"'", "quantity": 1000, "targetUrl": "https://open.spotify.com/track/test"}' \
  -w "\nHTTP Status: %{http_code}\n"

# Test 401 - Invalid API Key
curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "X-Api-Key: invalid-key" \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "'"${SERVICE_ID}"'", "quantity": 1000, "targetUrl": "https://open.spotify.com/track/test"}' \
  -w "\nHTTP Status: %{http_code}\n"

# Test 402 - Insufficient Balance (order too large)
curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "'"${SERVICE_ID}"'", "quantity": 1000000, "targetUrl": "https://open.spotify.com/track/test"}' | jq

# Test 404 - Service Not Found
curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "00000000-0000-0000-0000-000000000000", "quantity": 1000, "targetUrl": "https://open.spotify.com/track/test"}' \
  -w "\nHTTP Status: %{http_code}\n"

# Test 400 - Invalid Spotify URL
curl -X POST "${BASE_URL}/api/v2/orders" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "'"${SERVICE_ID}"'", "quantity": 1000, "targetUrl": "https://youtube.com/watch?v=test"}' \
  -w "\nHTTP Status: %{http_code}\n"
