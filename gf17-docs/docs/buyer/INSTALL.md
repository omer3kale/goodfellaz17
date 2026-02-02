# 🔧 Installation Guide - Spotify SMM Panel

**Complete deployment guide for all platforms**

---

## 📋 REQUIREMENTS

### Minimum
- Java 17+ (OpenJDK recommended)
- 512MB RAM
- PostgreSQL 15+ (Neon/Supabase free tier)

### Recommended (Production)
- Java 21 LTS
- 2GB RAM
- PostgreSQL with connection pooling
- CDN for static assets

---

## 🚀 DEPLOYMENT OPTIONS

### Option 1: Render.com (FREE - Recommended)

**Best for:** Getting started, $0 cost, auto-SSL

```bash
# 1. Create Render account
# 2. New → Web Service → Docker

# Environment Variables:
SPRING_PROFILES_ACTIVE=prod
NEON_HOST=your-project.neon.tech
NEON_USER=your_user
NEON_PASSWORD=your_password
NEON_DB=neondb
PORT=8080
JAVA_OPTS=-Xmx512m -Xms256m

# Deploy settings:
# - Region: Oregon (us-west)
# - Plan: Free (750h/mo)
# - Health check: /actuator/health
# - Auto-deploy: Yes
```

### Option 2: Railway.app

**Best for:** Simple Git deploy, $5/mo

```bash
# 1. Connect GitHub repo
# 2. Set environment variables
# 3. Deploy

# railway.toml (create file):
[build]
builder = "dockerfile"

[deploy]
healthcheckPath = "/actuator/health"
healthcheckTimeout = 300
```

### Option 3: Fly.io

**Best for:** Global edge deployment

```bash
# Install flyctl
brew install flyctl

# Login & deploy
fly auth login
fly launch --name spotify-smm

# Set secrets
fly secrets set NEON_HOST=xxx NEON_PASSWORD=xxx
```

### Option 4: VPS (DigitalOcean/Hetzner)

**Best for:** Full control, high traffic

```bash
# 1. Create Ubuntu 22.04 droplet ($6/mo)
ssh root@your-server

# 2. Install Java
apt update && apt install -y openjdk-17-jre-headless

# 3. Create service user
useradd -r -s /bin/false smmapp

# 4. Deploy JAR
mkdir -p /opt/smm
cp goodfellaz17-provider.jar /opt/smm/
chown -R smmapp:smmapp /opt/smm

# 5. Create systemd service
cat > /etc/systemd/system/smm.service << 'EOF'
[Unit]
Description=Spotify SMM Panel
After=network.target

[Service]
Type=simple
User=smmapp
WorkingDirectory=/opt/smm
ExecStart=/usr/bin/java -Xmx1g -jar goodfellaz17-provider.jar
Restart=always
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=NEON_HOST=your-db.neon.tech
Environment=NEON_USER=your_user
Environment=NEON_PASSWORD=your_password

[Install]
WantedBy=multi-user.target
EOF

# 6. Start service
systemctl daemon-reload
systemctl enable smm
systemctl start smm

# 7. Setup Nginx reverse proxy
apt install -y nginx certbot python3-certbot-nginx

cat > /etc/nginx/sites-available/smm << 'EOF'
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
EOF

ln -s /etc/nginx/sites-available/smm /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

# 8. SSL certificate
certbot --nginx -d your-domain.com
```

### Option 5: Docker Compose (Local/Server)

```bash
# Use demo compose file
docker-compose -f docker-compose.demo.yml up -d

# Production with external DB
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e NEON_HOST=your-db.neon.tech \
  -e NEON_PASSWORD=xxx \
  --name smm-panel \
  ghcr.io/your-org/spotify-smm:latest
```

---

## 🗄️ DATABASE SETUP

### Option A: Neon (Recommended - FREE)

```bash
# 1. Create account at neon.tech
# 2. Create project (choose us-east-1 or eu-central-1)
# 3. Get connection string

# Environment variables:
NEON_HOST=ep-xxx-pooler.us-east-1.aws.neon.tech
NEON_USER=your_user
NEON_PASSWORD=generated_password
NEON_DB=neondb
```

### Option B: Supabase (Alternative)

```bash
# 1. Create project at supabase.com
# 2. Settings → Database → Connection string

SUPABASE_URL=https://xxx.supabase.co
SUPABASE_KEY=your-anon-key
```

### Database Schema

The application auto-creates tables on first run with `ddl-auto: create`.

For production, use provided SQL:

```bash
psql $DATABASE_URL < scripts/init.sql
```

---

## ⚙️ CONFIGURATION

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | dev | Use `prod` for production |
| `NEON_HOST` | Yes | - | Database host |
| `NEON_USER` | Yes | - | Database user |
| `NEON_PASSWORD` | Yes | - | Database password |
| `NEON_DB` | No | neondb | Database name |
| `PORT` | No | 8080 | Server port |
| `JAVA_OPTS` | No | - | JVM options |

### Application Properties

Edit `src/main/resources/application-prod.yml` for custom settings:

```yaml
goodfellaz17:
  smi:
    enabled: true
    symboltable:
      cache-size: 1000      # Service cache
    cocos:
      order-quantity: true  # Validation
      spotify-drip: true    # Drip limits

bot:
  executor:
    max-concurrent: 100     # Parallel bots

arbitrage:
  commission-rate: 0.30     # 30% to users
  rate-per-thousand: 0.50   # Base rate
```

---

## ✅ HEALTH CHECKS

### Verify Deployment

```bash
# Health endpoint
curl https://your-domain.com/actuator/health

# Expected response:
{"status":"UP"}

# Services endpoint
curl -X POST https://your-domain.com/api/v2 \
  -d "key=demo&action=services"

# Expected: List of 9 services
```

### Monitoring Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status |
| `/actuator/metrics` | Prometheus metrics |
| `/actuator/info` | App info |

---

## 🔒 SECURITY CHECKLIST

- [ ] Use HTTPS (auto with Render/Railway)
- [ ] Set strong `NEON_PASSWORD`
- [ ] Enable rate limiting
- [ ] Configure CORS for your domain
- [ ] Use environment variables (never commit secrets)
- [ ] Regular database backups

---

## 🆘 TROUBLESHOOTING

### Common Issues

**App won't start:**

```bash
# Check logs
docker logs smm-panel
# or
journalctl -u smm -f
```

**Database connection failed:**

```bash
# Test connection
psql "postgresql://user:pass@host:5432/db?sslmode=require"
```

**Out of memory:**

```bash
# Increase heap
JAVA_OPTS="-Xmx1g -Xms512m"
```

**Port already in use:**

```bash
# Change port
PORT=8081
```

---

## 📞 SUPPORT

- Documentation: [API_DOCS.md](API_DOCS.md)
- Issues: GitHub Issues
- Updates: Star the repo!

---

**🎵 GOODFELLAZ17 - Spotify SMM Panel v1.0**
