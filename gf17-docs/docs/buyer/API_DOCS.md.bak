# 📡 API Documentation - Spotify SMM Panel

**Full SMM Panel API v2 Specification**

---

## 🔐 Authentication

All API calls require an API key:

```
key=YOUR_API_KEY
```

Get your API key from the PWA dashboard after registration.

---

## 📋 Endpoints

### Base URL

```
POST https://your-domain.com/api/v2
```

All requests use **POST** with form-encoded body.

---

## 🎵 SERVICES

### List All Services

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=services"
```

**Response:**

```json
{
  "services": [
    {
      "service": 1,
      "name": "Spotify Plays Worldwide",
      "type": "Default",
      "category": "Spotify",
      "rate": 0.50,
      "min": 100,
      "max": 10000000,
      "refill": false,
      "cancel": true
    },
    {
      "service": 2,
      "name": "Spotify Plays USA",
      "type": "Default",
      "category": "Spotify",
      "rate": 0.90,
      "min": 100,
      "max": 5000000,
      "refill": false,
      "cancel": true
    }
  ],
  "count": 9
}
```

---

## 🛒 ORDERS

### Place Order

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=add&service=1&link=https://open.spotify.com/track/xxx&quantity=1000"
```

**Parameters:**
| Parameter  | Required | Description |
|------------|----------|-------------|
| key        | Yes      | API key |
| action     | Yes      | `add` |
| service    | Yes      | Service ID (1-11) |
| link       | Yes      | Spotify track/playlist URL |
| quantity   | Yes      | Number of plays/followers |

**Response:**

```json
{
  "order": 12345,
  "charge": 0.50
}
```

### Order Status

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=status&order=12345"
```

**Response:**

```json
{
  "order": 12345,
  "status": "In progress",
  "charge": 0.50,
  "start_count": 1000,
  "remains": 500
}
```

**Status Values:**
- `Pending` - Order queued
- `In progress` - Currently processing
- `Completed` - Order finished
- `Partial` - Partial completion
- `Canceled` - Order canceled
- `Refunded` - Funds returned

### Multiple Order Status

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=status&orders=12345,12346,12347"
```

---

## 💰 BALANCE

### Check Balance

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=balance"
```

**Response:**

```json
{
  "balance": 150.25,
  "currency": "USD"
}
```

---

## 🔄 REFILLS (Coming Soon)

### Create Refill

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=refill&order=12345"
```

### Refill Status

```bash
curl -X POST https://your-domain.com/api/v2 \
  -d "key=YOUR_KEY&action=refill_status&refill=456"
```

---

## ❌ ERROR CODES

| Code | Message | Solution |
|------|---------|----------|
| `invalid_key` | Invalid API key | Check your API key |
| `insufficient_balance` | Not enough funds | Add balance |
| `invalid_service` | Service doesn't exist | Check service ID |
| `invalid_link` | Invalid Spotify URL | Use open.spotify.com links |
| `min_quantity` | Below minimum | Check service min |
| `max_quantity` | Above maximum | Check service max |
| `order_not_found` | Order ID invalid | Verify order ID |

**Error Response:**

```json
{
  "error": "invalid_key",
  "message": "Invalid API key provided"
}
```

---

## 📊 RATE LIMITS

| Plan    | Requests/min | Orders/day |
|---------|--------------|------------|
| Free    | 60           | 100        |
| Pro     | 300          | 1,000      |
| Agency  | 1,000        | Unlimited  |

---

## 🎯 SERVICE CATALOG

| ID | Service | Rate/1k | Min | Max |
|----|---------|---------|-----|-----|
| 1  | Spotify Plays Worldwide | $0.50 | 100 | 10M |
| 2  | Spotify Plays USA | $0.90 | 100 | 5M |
| 3  | Monthly Listeners USA | $1.90 | 500 | 1M |
| 4  | Monthly Listeners Global | $1.50 | 500 | 2M |
| 5  | Spotify Followers | $2.00 | 100 | 500K |
| 6  | Spotify Saves | $1.00 | 100 | 1M |
| 7  | Playlist Followers | $1.50 | 100 | 500K |
| 10 | Plays Drip Feed (24h) | $0.60 | 1K | 1M |
| 11 | Monthly Drip USA (30d) | $2.50 | 1K | 500K |

---

## 💻 CODE EXAMPLES

### PHP

```php
<?php
$ch = curl_init('https://your-domain.com/api/v2');
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, [
    'key' => 'YOUR_API_KEY',
    'action' => 'add',
    'service' => 1,
    'link' => 'https://open.spotify.com/track/xxx',
    'quantity' => 1000
]);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
$response = json_decode(curl_exec($ch));
echo "Order ID: " . $response->order;
```

### Python

```python
import requests

response = requests.post('https://your-domain.com/api/v2', data={
    'key': 'YOUR_API_KEY',
    'action': 'add',
    'service': 1,
    'link': 'https://open.spotify.com/track/xxx',
    'quantity': 1000
})
print(f"Order ID: {response.json()['order']}")
```

### JavaScript

```javascript
fetch('https://your-domain.com/api/v2', {
    method: 'POST',
    body: new URLSearchParams({
        key: 'YOUR_API_KEY',
        action: 'add',
        service: 1,
        link: 'https://open.spotify.com/track/xxx',
        quantity: 1000
    })
})
.then(r => r.json())
.then(data => console.log('Order:', data.order));
```

---

**🎵 GOODFELLAZ17 - Spotify SMM Panel API v2**
