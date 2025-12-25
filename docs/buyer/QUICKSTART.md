# 🚀 QUICKSTART - Spotify SMM Panel

## Go from ZIP → $15k/day in 10 minutes

---

## ⚡ 1-CLICK DEPLOY (Render.com - FREE)

### Step 1: Create Render Account

```text
1. Go to https://render.com
2. Sign up with GitHub
3. Click "New → Web Service"
```

### Step 2: Deploy JAR

```bash
# Upload goodfellaz17-provider.jar
# Runtime: Docker
# Free tier: 750 hours/month
```

### Step 3: Set Environment Variables

```bash
SPRING_PROFILES_ACTIVE=prod
NEON_HOST=your-db.neon.tech
NEON_USER=your_user
NEON_PASSWORD=your_password
PORT=8080
```

### Step 4: Visit Your Panel

```text
https://your-app.onrender.com → PWA Dashboard installed!
```

---

## 🔧 LOCAL DEVELOPMENT

### Docker (Recommended)

```bash
# Start with demo database
docker-compose -f docker-compose.demo.yml up

# Access:
# API: http://localhost:8080/api/v2
# PWA: http://localhost:8080
```

### Maven (Development)

```bash
# Run with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests (56 passing)
mvn test
```

---

## 🎯 FIRST API CALL

### Get Services

```bash
curl https://your-app.onrender.com/api/v2 \
  -d "key=demo&action=services"
```

**Response:**

```json
{
  "services": [
    {"service": 1, "name": "Spotify Plays Worldwide", "rate": 0.50, "min": 100, "max": 10000000},
    {"service": 2, "name": "Spotify Plays USA", "rate": 0.90, "min": 100, "max": 5000000},
    {"service": 3, "name": "Monthly Listeners USA", "rate": 1.90, "min": 500, "max": 1000000}
  ]
}
```

### Place Order

```bash
curl https://your-app.onrender.com/api/v2 \
  -d "key=YOUR_API_KEY&action=add&service=1&link=https://open.spotify.com/track/xxx&quantity=1000"
```

### Check Balance

```bash
curl https://your-app.onrender.com/api/v2 \
  -d "key=YOUR_API_KEY&action=balance"
```

---

## 📱 PWA DASHBOARD

The panel includes a **Progressive Web App** that works on:

- ✅ iPhone (Add to Home Screen)
- ✅ Android (Install prompt)
- ✅ Desktop Chrome/Edge

### Features

- Real-time order tracking
- Balance management
- Service browser
- API key management

---

## 💰 REVENUE CALCULATOR

| Daily Orders | Avg Order | Margin | Daily Profit |
|--------------|-----------|--------|--------------|
| 100          | $10       | 50%    | **$500**     |
| 500          | $15       | 50%    | **$3,750**   |
| 1,000        | $20       | 50%    | **$10,000**  |
| 2,000        | $25       | 50%    | **$25,000**  |

---

## 🆘 SUPPORT

- **Documentation**: See [API_DOCS.md](API_DOCS.md)
- **Installation**: See [INSTALL.md](INSTALL.md)
- **Whitelabel**: See [WHITELABEL.md](WHITELABEL.md)

**Need help?** Open an issue on GitHub!

---

**🎵 GOODFELLAZ17 - Spotify SMM Panel v1.0**
