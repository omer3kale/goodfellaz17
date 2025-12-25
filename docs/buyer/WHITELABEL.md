# 🎨 Whitelabel Guide - Spotify SMM Panel

**Complete rebranding guide for resellers**

---

## 🏷️ QUICK REBRAND (10 minutes)

### Step 1: Change Application Name

Edit `pom.xml`:
```xml
<artifactId>your-brand-provider</artifactId>
<name>Your Brand SMM Panel</name>
```

Edit `application.yml`:
```yaml
spring:
  application:
    name: your-brand-provider
```

### Step 2: Update PWA Branding

Edit `src/main/resources/static/manifest.json`:
```json
{
  "name": "Your Brand SMM",
  "short_name": "YourBrand",
  "description": "Premium Spotify Services",
  "theme_color": "#your-color",
  "background_color": "#ffffff"
}
```

### Step 3: Replace Icons

Replace files in `src/main/resources/static/`:
- `icon-192.png` - App icon (192x192)
- `icon-512.png` - Splash icon (512x512)
- `favicon.ico` - Browser tab icon

### Step 4: Customize PWA Dashboard

Edit `src/main/resources/static/index.html`:
```html
<title>Your Brand SMM</title>
<meta name="description" content="Your Brand - Premium Spotify Services">

<!-- Logo -->
<img src="your-logo.svg" alt="Your Brand">

<!-- Colors (CSS variables) -->
<style>
:root {
  --primary: #your-primary-color;
  --secondary: #your-secondary-color;
  --accent: #your-accent-color;
}
</style>
```

---

## 📦 PACKAGE RENAMING (Optional - Advanced)

### Rename Java Package

```bash
# From: com.goodfellaz17
# To: com.yourbrand

# 1. Rename directories
mv src/main/java/com/goodfellaz17 src/main/java/com/yourbrand

# 2. Update all Java files
find src -name "*.java" -exec sed -i 's/com.goodfellaz17/com.yourbrand/g' {} \;

# 3. Update pom.xml groupId
sed -i 's/<groupId>com.goodfellaz17/<groupId>com.yourbrand/' pom.xml

# 4. Rebuild
mvn clean package
```

### Files to Update

| File | Change |
|------|--------|
| `pom.xml` | groupId, artifactId, name |
| `GoodfellazApplication.java` | Class name → `YourBrandApplication.java` |
| `application.yml` | spring.application.name |
| `Dockerfile` | JAR name reference |
| `README.md` | All branding |

---

## 🎨 VISUAL CUSTOMIZATION

### Color Schemes

**Default (Spotify Green):**
```css
--primary: #1DB954;
--secondary: #191414;
--accent: #1ed760;
```

**Alternative Options:**
```css
/* Ocean Blue */
--primary: #0077B5;
--secondary: #003366;
--accent: #00A0DC;

/* Purple Premium */
--primary: #7C3AED;
--secondary: #1F1B24;
--accent: #A855F7;

/* Gold Luxury */
--primary: #D4AF37;
--secondary: #1A1A1A;
--accent: #FFD700;
```

### Logo Guidelines

- **Format:** SVG preferred (scales perfectly)
- **Size:** 200x50px horizontal logo
- **Favicon:** 32x32px, simple icon
- **PWA Icons:** 192x192 and 512x512

### Email Templates (Coming Soon)

Edit templates in `src/main/resources/templates/`:
- `welcome.html` - New user welcome
- `order-complete.html` - Order confirmation
- `low-balance.html` - Balance alert

---

## 🌐 DOMAIN SETUP

### Custom Domain on Render

1. Go to Render Dashboard → Your Service
2. Settings → Custom Domains
3. Add domain: `smm.yourbrand.com`
4. Configure DNS:
   ```
   CNAME smm → your-app.onrender.com
   ```

### SSL Certificate

Render auto-provisions Let's Encrypt SSL.

For custom certs:
```bash
# Upload to Render or your server
certbot certonly --webroot -w /var/www/html -d smm.yourbrand.com
```

---

## 💳 PAYMENT GATEWAY INTEGRATION

### Stripe (Recommended)

```yaml
# application-prod.yml
stripe:
  api-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  currency: USD
```

### PayPal

```yaml
paypal:
  client-id: ${PAYPAL_CLIENT_ID}
  client-secret: ${PAYPAL_CLIENT_SECRET}
  mode: live  # or sandbox
```

### Crypto (CoinGate)

```yaml
coingate:
  api-key: ${COINGATE_API_KEY}
  environment: live
  receive-currency: USD
```

---

## 📧 EMAIL CONFIGURATION

### SMTP Setup

```yaml
# application-prod.yml
spring:
  mail:
    host: smtp.your-provider.com
    port: 587
    username: ${SMTP_USER}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

### Recommended Providers

| Provider | Free Tier | Notes |
|----------|-----------|-------|
| Mailgun | 5k/mo | Best deliverability |
| SendGrid | 100/day | Easy setup |
| Amazon SES | 62k/mo | Cheapest at scale |

---

## 🔧 API CUSTOMIZATION

### Change API Path

Edit `SmmProviderController.java`:
```java
@RestController
@RequestMapping("/api/v2")  // Change to "/your-api"
public class SmmProviderController {
```

### Add Custom Endpoints

```java
@PostMapping("/custom")
public Mono<ResponseEntity<?>> customEndpoint(
    @RequestParam String key,
    @RequestParam String action
) {
    // Your custom logic
}
```

### Rate Limiting

```yaml
# application-prod.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 100
        limit-refresh-period: 1m
```

---

## 🏢 MULTI-TENANT (Enterprise)

For running multiple brands from one codebase:

```yaml
# application-prod.yml
tenants:
  brand-a:
    domain: brand-a.com
    name: Brand A SMM
    theme: blue
  brand-b:
    domain: brand-b.com  
    name: Brand B Panel
    theme: purple
```

---

## ✅ REBRAND CHECKLIST

- [ ] Update `pom.xml` (artifactId, name)
- [ ] Change `application.yml` (app name)
- [ ] Replace PWA icons (192, 512, favicon)
- [ ] Edit `manifest.json` (name, colors)
- [ ] Customize `index.html` (logo, colors)
- [ ] Update `README.md`
- [ ] Configure custom domain
- [ ] Set up payment gateway
- [ ] Configure email templates
- [ ] Test all endpoints

---

## 📞 WHITELABEL SUPPORT

Need help with custom branding?

- **Basic Whitelabel:** Included with purchase
- **Full Rebrand Service:** $297 one-time
- **Custom Development:** Contact us

---

**🎵 GOODFELLAZ17 - Spotify SMM Panel v1.0**
