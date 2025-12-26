package com.goodfellaz17.infrastructure.notification;

import com.goodfellaz17.infrastructure.persistence.entity.OrderEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Email Notification Service - Resend.com integration.
 * 
 * Sends order status emails at key milestones:
 * - Order Created (Pending)
 * - Processing Started
 * - Progress Updates (25%, 50%, 75%)
 * - Order Completed
 * - Order Failed + Refund
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final Resend resend;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:orders@goodfellaz17.com}")
    private String fromEmail;

    @Value("${resend.enabled:true}")
    private boolean emailEnabled;

    public EmailService(ApiKeyRepository apiKeyRepository,
                       @Value("${resend.api-key:}") String resendApiKey) {
        this.apiKeyRepository = apiKeyRepository;
        this.resend = new Resend(resendApiKey);
    }

    /**
     * Send order created notification.
     */
    public Mono<Void> sendOrderCreated(OrderEntity order) {
        String subject = "⚡ Order Created - " + order.getOrderId().toString().substring(0, 8);
        String body = buildOrderCreatedEmail(order);
        return sendEmail(order.getApiKey(), subject, body);
    }

    /**
     * Send processing started notification.
     */
    public Mono<Void> sendProcessingStarted(OrderEntity order) {
        String subject = "🚀 Order Processing - " + order.getOrderId().toString().substring(0, 8);
        String body = buildProcessingEmail(order);
        return sendEmail(order.getApiKey(), subject, body);
    }

    /**
     * Send progress update notification.
     */
    public Mono<Void> sendProgressUpdate(OrderEntity order, int progressPercent) {
        String subject = "📊 " + progressPercent + "% Complete - " + order.getOrderId().toString().substring(0, 8);
        String body = buildProgressEmail(order, progressPercent);
        return sendEmail(order.getApiKey(), subject, body);
    }

    /**
     * Send order completed notification.
     */
    public Mono<Void> sendOrderCompleted(OrderEntity order) {
        String subject = "✅ Order Completed - " + order.getQuantity() + " delivered!";
        String body = buildCompletedEmail(order);
        return sendEmail(order.getApiKey(), subject, body);
    }

    /**
     * Send order failed + refund notification.
     */
    public Mono<Void> sendOrderFailed(OrderEntity order, BigDecimal refundAmount) {
        String subject = "❌ Order Failed - $" + refundAmount + " Refunded";
        String body = buildFailedEmail(order, refundAmount);
        return sendEmail(order.getApiKey(), subject, body);
    }

    /**
     * Core email sending logic using Resend API.
     */
    private Mono<Void> sendEmail(String apiKey, String subject, String htmlBody) {
        if (!emailEnabled || resendApiKey == null || resendApiKey.isBlank()) {
            log.debug("Email disabled or API key not set, skipping: {}", subject);
            return Mono.empty();
        }

        // Look up user email from API key (userName field stores email)
        return apiKeyRepository.findByApiKey(apiKey)
            .flatMap(keyEntity -> {
                String userEmail = keyEntity.getUserName();
                if (userEmail == null || !userEmail.contains("@")) {
                    log.debug("No valid email for API key: {}", apiKey);
                    return Mono.empty();
                }

                return Mono.fromCallable(() -> {
                    try {
                        CreateEmailOptions params = CreateEmailOptions.builder()
                            .from("GOODFELLAZ17 <" + fromEmail + ">")
                            .to(userEmail)
                            .subject(subject)
                            .html(htmlBody)
                            .build();

                        CreateEmailResponse response = resend.emails().send(params);
                        log.info("Email sent to {}: {} (id: {})", userEmail, subject, response.getId());
                        return response;
                    } catch (ResendException e) {
                        log.error("Failed to send email to {}: {}", userEmail, e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
            })
            .onErrorResume(e -> {
                log.error("Email error: {}", e.getMessage());
                return Mono.empty();
            });
    }

    // ==================== EMAIL TEMPLATES ====================

    private String buildOrderCreatedEmail(OrderEntity order) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Inter', sans-serif; background: #0a0a0a; color: #fff; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { color: #00ff88; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
                    .card { background: rgba(255,255,255,0.08); border: 1px solid rgba(0,255,136,0.3); border-radius: 12px; padding: 20px; }
                    .label { color: rgba(255,255,255,0.6); font-size: 12px; text-transform: uppercase; }
                    .value { font-size: 18px; font-weight: 600; margin-bottom: 15px; }
                    .status { background: rgba(255,193,7,0.2); color: #ffc107; padding: 8px 16px; border-radius: 20px; display: inline-block; }
                    .footer { margin-top: 20px; color: rgba(255,255,255,0.5); font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">⚡ GOODFELLAZ17</div>
                    <div class="card">
                        <div class="status">📋 Pending</div>
                        <h2>Order Created Successfully</h2>
                        <div class="label">Order ID</div>
                        <div class="value">%s</div>
                        <div class="label">Quantity</div>
                        <div class="value">%,d plays</div>
                        <div class="label">Amount Charged</div>
                        <div class="value">$%.2f</div>
                        <div class="label">Link</div>
                        <div class="value" style="word-break: break-all;">%s</div>
                        <p>Your order has been queued for processing. You'll receive updates as it progresses.</p>
                    </div>
                    <div class="footer">
                        Track your order: <a href="https://goodfellaz17.onrender.com/customer?key=%s" style="color: #00ff88;">Dashboard</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                order.getOrderId().toString().substring(0, 8),
                order.getQuantity(),
                order.getCharged(),
                order.getLink(),
                order.getApiKey()
            );
    }

    private String buildProcessingEmail(OrderEntity order) {
        String eta = formatEta(order.getEtaMinutes());
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Inter', sans-serif; background: #0a0a0a; color: #fff; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { color: #00ff88; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
                    .card { background: rgba(255,255,255,0.08); border: 1px solid rgba(0,212,255,0.3); border-radius: 12px; padding: 20px; }
                    .status { background: rgba(0,212,255,0.2); color: #00d4ff; padding: 8px 16px; border-radius: 20px; display: inline-block; }
                    .progress-bar { background: rgba(255,255,255,0.1); border-radius: 10px; height: 20px; margin: 15px 0; overflow: hidden; }
                    .progress-fill { background: linear-gradient(90deg, #00ff88, #00d4ff); height: 100%%; width: 5%%; border-radius: 10px; }
                    .footer { margin-top: 20px; color: rgba(255,255,255,0.5); font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">⚡ GOODFELLAZ17</div>
                    <div class="card">
                        <div class="status">🚀 Processing</div>
                        <h2>Order Now Processing</h2>
                        <div class="progress-bar"><div class="progress-fill"></div></div>
                        <p><strong>ETA:</strong> %s</p>
                        <p><strong>Order:</strong> %s</p>
                        <p><strong>Quantity:</strong> %,d plays</p>
                        <p>Our bots are actively working on your order. Sit back and relax!</p>
                    </div>
                    <div class="footer">
                        Track live: <a href="https://goodfellaz17.onrender.com/customer?key=%s" style="color: #00ff88;">Dashboard</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                eta,
                order.getOrderId().toString().substring(0, 8),
                order.getQuantity(),
                order.getApiKey()
            );
    }

    private String buildProgressEmail(OrderEntity order, int progressPercent) {
        String eta = formatEta(order.getEtaMinutes());
        int delivered = (order.getQuantity() * progressPercent) / 100;
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Inter', sans-serif; background: #0a0a0a; color: #fff; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { color: #00ff88; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
                    .card { background: rgba(255,255,255,0.08); border: 1px solid rgba(0,212,255,0.3); border-radius: 12px; padding: 20px; }
                    .big-number { font-size: 48px; font-weight: bold; color: #00ff88; }
                    .progress-bar { background: rgba(255,255,255,0.1); border-radius: 10px; height: 20px; margin: 15px 0; overflow: hidden; }
                    .progress-fill { background: linear-gradient(90deg, #00ff88, #00d4ff); height: 100%%; width: %d%%; border-radius: 10px; }
                    .footer { margin-top: 20px; color: rgba(255,255,255,0.5); font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">⚡ GOODFELLAZ17</div>
                    <div class="card">
                        <div class="big-number">%d%%</div>
                        <div class="progress-bar"><div class="progress-fill"></div></div>
                        <p><strong>Delivered:</strong> %,d / %,d plays</p>
                        <p><strong>Remaining ETA:</strong> %s</p>
                        <p><strong>Order:</strong> %s</p>
                    </div>
                    <div class="footer">
                        Track live: <a href="https://goodfellaz17.onrender.com/customer?key=%s" style="color: #00ff88;">Dashboard</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                progressPercent,
                progressPercent,
                delivered,
                order.getQuantity(),
                eta,
                order.getOrderId().toString().substring(0, 8),
                order.getApiKey()
            );
    }

    private String buildCompletedEmail(OrderEntity order) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Inter', sans-serif; background: #0a0a0a; color: #fff; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { color: #00ff88; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
                    .card { background: rgba(255,255,255,0.08); border: 1px solid rgba(0,255,136,0.5); border-radius: 12px; padding: 20px; }
                    .status { background: rgba(0,255,136,0.2); color: #00ff88; padding: 8px 16px; border-radius: 20px; display: inline-block; font-size: 18px; }
                    .big-check { font-size: 64px; text-align: center; margin: 20px 0; }
                    .footer { margin-top: 20px; color: rgba(255,255,255,0.5); font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">⚡ GOODFELLAZ17</div>
                    <div class="card">
                        <div class="big-check">✅</div>
                        <div class="status">Completed</div>
                        <h2>Order Delivered Successfully!</h2>
                        <p><strong>%,d plays</strong> have been delivered to your track.</p>
                        <p><strong>Order:</strong> %s</p>
                        <p><strong>Link:</strong> %s</p>
                        <p style="margin-top: 20px;">Thank you for using GOODFELLAZ17! Ready for another boost?</p>
                    </div>
                    <div class="footer">
                        Order more: <a href="https://goodfellaz17.onrender.com/customer?key=%s" style="color: #00ff88;">Dashboard</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                order.getQuantity(),
                order.getOrderId().toString().substring(0, 8),
                order.getLink(),
                order.getApiKey()
            );
    }

    private String buildFailedEmail(OrderEntity order, BigDecimal refundAmount) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Inter', sans-serif; background: #0a0a0a; color: #fff; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { color: #00ff88; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
                    .card { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,0,128,0.5); border-radius: 12px; padding: 20px; }
                    .status { background: rgba(255,0,128,0.2); color: #ff0080; padding: 8px 16px; border-radius: 20px; display: inline-block; }
                    .refund { background: rgba(0,255,136,0.1); border: 1px solid rgba(0,255,136,0.3); border-radius: 8px; padding: 15px; margin: 15px 0; }
                    .refund-amount { font-size: 24px; color: #00ff88; font-weight: bold; }
                    .footer { margin-top: 20px; color: rgba(255,255,255,0.5); font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">⚡ GOODFELLAZ17</div>
                    <div class="card">
                        <div class="status">❌ Failed</div>
                        <h2>Order Could Not Complete</h2>
                        <p><strong>Order:</strong> %s</p>
                        <p>We encountered an issue processing your order. Don't worry - you've been refunded!</p>
                        <div class="refund">
                            <p style="margin: 0; color: rgba(255,255,255,0.7);">Refunded to Balance:</p>
                            <div class="refund-amount">$%.2f</div>
                        </div>
                        <p>Your balance has been restored. You can place a new order anytime.</p>
                    </div>
                    <div class="footer">
                        Check balance: <a href="https://goodfellaz17.onrender.com/customer?key=%s" style="color: #00ff88;">Dashboard</a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                order.getOrderId().toString().substring(0, 8),
                refundAmount,
                order.getApiKey()
            );
    }

    private String formatEta(Integer etaMinutes) {
        if (etaMinutes == null || etaMinutes <= 0) {
            return "Calculating...";
        }
        if (etaMinutes < 60) {
            return etaMinutes + " minutes";
        }
        int hours = etaMinutes / 60;
        int mins = etaMinutes % 60;
        if (hours < 24) {
            return hours + "h " + mins + "m";
        }
        int days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }
}
