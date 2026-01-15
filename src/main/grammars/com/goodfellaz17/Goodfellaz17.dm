/* 
 * MontiCore Domain Model Instance: Goodfellaz17.dm
 * 
 * Concrete domain model for the goodfellaz17 Spotify bot delivery engine.
 * This file is parsed by the DomainModel.mc4 grammar and generates:
 *   - Java JPA Entities
 *   - DTOs (Request/Response)
 *   - PostgreSQL DDL
 *   - Spring Data Repositories
 *   - Flyway Migrations
 * 
 * RWTH Aachen MATSE Thesis: Distributed Proxy Orchestration
 */

domain Goodfellaz17 {

  // ============================================================
  // ENUMERATIONS
  // ============================================================
  
  enum UserTier {
    CONSUMER = "consumer",
    RESELLER = "reseller",
    AGENCY = "agency"
  }
  
  enum UserStatus {
    ACTIVE = "active",
    SUSPENDED = "suspended",
    PENDING_VERIFICATION = "pending_verification"
  }
  
  enum ServiceType {
    PLAYS = "plays",
    MONTHLY_LISTENERS = "monthly_listeners",
    SAVES = "saves",
    FOLLOWS = "follows",
    PLAYLIST_FOLLOWERS = "playlist_followers",
    PLAYLIST_PLAYS = "playlist_plays"
  }
  
  enum OrderStatus {
    PENDING = "pending",
    VALIDATING = "validating",
    RUNNING = "running",
    COMPLETED = "completed",
    PARTIAL = "partial",
    FAILED = "failed",
    REFUNDED = "refunded",
    CANCELLED = "cancelled"
  }
  
  enum GeoProfile {
    WORLDWIDE = "worldwide",
    USA = "usa",
    UK = "uk",
    DE = "de",
    FR = "fr",
    BR = "br",
    MX = "mx",
    LATAM = "latam",
    EUROPE = "europe",
    ASIA = "asia",
    PREMIUM_MIX = "premium_mix"
  }
  
  enum TransactionType {
    DEBIT = "debit",
    CREDIT = "credit",
    REFUND = "refund",
    BONUS = "bonus",
    ADJUSTMENT = "adjustment"
  }
  
  enum ProxyProvider {
    VULTR = "vultr",
    HETZNER = "hetzner",
    NETCUP = "netcup",
    CONTABO = "contabo",
    OVH = "ovh",
    AWS = "aws",
    DIGITALOCEAN = "digitalocean",
    LINODE = "linode"
  }
  
  enum ProxyTier {
    DATACENTER = "datacenter",
    ISP = "isp",
    TOR = "tor",
    RESIDENTIAL = "residential",
    MOBILE = "mobile"
  }
  
  enum ProxyStatus {
    ONLINE = "online",
    OFFLINE = "offline",
    MAINTENANCE = "maintenance",
    BANNED = "banned",
    RATE_LIMITED = "rate_limited"
  }
  
  enum DeviceType {
    PHONE_ANDROID = "phone_android",
    PHONE_IOS = "phone_ios",
    EMULATOR_ANDROID = "emulator_android",
    EMULATOR_IOS = "emulator_ios",
    DESKTOP_WEB = "desktop_web",
    MOBILE_WEB = "mobile_web"
  }
  
  enum DeviceStatus {
    ACTIVE = "active",
    IDLE = "idle",
    MAINTENANCE = "maintenance",
    RETIRED = "retired"
  }
  
  enum CircuitStatus {
    ACTIVE = "active",
    RETIRED = "retired",
    EXPIRED = "expired",
    BLOCKED = "blocked"
  }

  // ============================================================
  // CORE ENTITIES
  // ============================================================
  
  @Table(name = "users")
  @Audited
  entity User {
    @Generated(UUID)
    id: UUID @NotNull;
    
    email: String @NotNull @Unique @Length(max = 255);
    passwordHash: String @NotNull @Length(max = 255);
    tier: String @NotNull @EnumType(UserTier) @Default(UserTier.CONSUMER);
    balance: BigDecimal @NotNull @Default(0.00) @Range(min = 0, max = 999999.99);
    apiKey: String @Nullable @Unique @Length(max = 64);
    webhookUrl: String @Nullable @Length(max = 512);
    discordWebhook: String @Nullable @Length(max = 512);
    
    companyName: String @Nullable @Length(max = 255);
    referralCode: String @Nullable @Unique @Length(max = 32);
    referredBy: Optional<UUID>;
    
    createdAt: Instant @NotNull @Default(Instant.now());
    lastLogin: Optional<Instant>;
    status: String @NotNull @EnumType(UserStatus) @Default(UserStatus.ACTIVE);
    
    emailVerified: Boolean @NotNull @Default(false);
    twoFactorEnabled: Boolean @NotNull @Default(false);
    
    @Index(name = "idx_users_email", columns = [email], unique = true)
    @Index(name = "idx_users_api_key", columns = [apiKey], unique = true)
    @Index(name = "idx_users_tier_status", columns = [tier, status])
  }
  
  @Table(name = "services")
  entity Service {
    @Generated(UUID)
    id: UUID @NotNull;
    
    name: String @NotNull @Unique @Length(max = 100);
    displayName: String @NotNull @Length(max = 255);
    serviceType: String @NotNull @EnumType(ServiceType);
    description: String @Nullable @Length(max = 1000);
    
    costPer1k: BigDecimal @NotNull @Range(min = 0.01, max = 999.99);
    resellerCostPer1k: BigDecimal @NotNull @Range(min = 0.01, max = 999.99);
    agencyCostPer1k: BigDecimal @NotNull @Range(min = 0.01, max = 999.99);
    
    minQuantity: Int @NotNull @Default(100) @Range(min = 1, max = 1000000);
    maxQuantity: Int @NotNull @Default(1000000) @Range(min = 1, max = 10000000);
    estimatedDaysMin: Int @NotNull @Default(1);
    estimatedDaysMax: Int @NotNull @Default(7);
    
    geoProfiles: List<String> @NotNull; // JSON array of GeoProfile values
    
    isActive: Boolean @NotNull @Default(true);
    sortOrder: Int @NotNull @Default(0);
    
    createdAt: Instant @NotNull @Default(Instant.now());
    updatedAt: Instant @NotNull @Default(Instant.now());
    
    @Index(name = "idx_services_type_active", columns = [serviceType, isActive])
    @Index(name = "idx_services_sort", columns = [sortOrder])
  }
  
  @Table(name = "orders")
  @Audited
  entity Order {
    @Generated(UUID)
    id: UUID @NotNull;
    
    userId: UUID @NotNull @Indexed;
    serviceId: UUID @NotNull @Indexed;
    
    quantity: Int @NotNull @Range(min = 1, max = 10000000);
    delivered: Int @NotNull @Default(0);
    targetUrl: String @NotNull @Length(max = 512) @Pattern("^https://(open\\.)?spotify\\.com/.*$");
    
    geoProfile: String @NotNull @EnumType(GeoProfile) @Default(GeoProfile.WORLDWIDE);
    speedMultiplier: Double @NotNull @Default(1.0) @Range(min = 0.1, max = 5.0);
    
    status: String @NotNull @EnumType(OrderStatus) @Default(OrderStatus.PENDING);
    
    cost: BigDecimal @NotNull @Range(min = 0.00, max = 99999.99);
    refundAmount: BigDecimal @NotNull @Default(0.00);
    
    startCount: Optional<Int>; // Initial count when order started
    currentCount: Optional<Int>; // Latest scraped count
    
    createdAt: Instant @NotNull @Default(Instant.now());
    startedAt: Optional<Instant>;
    completedAt: Optional<Instant>;
    
    failureReason: String @Nullable @Length(max = 500);
    internalNotes: String @Nullable @Length(max = 1000);
    
    externalOrderId: String @Nullable @Length(max = 64); // For API integrations
    webhookDelivered: Boolean @NotNull @Default(false);
    
    @Index(name = "idx_orders_user_status", columns = [userId, status])
    @Index(name = "idx_orders_status_created", columns = [status, createdAt])
    @Index(name = "idx_orders_target_url", columns = [targetUrl])
  }
  
  @Table(name = "balance_transactions")
  @Audited
  entity BalanceTransaction {
    @Generated(UUID)
    id: UUID @NotNull;
    
    userId: UUID @NotNull @Indexed;
    orderId: Optional<UUID>; // Null for deposits, bonuses
    
    amount: BigDecimal @NotNull; // Positive for credit, negative for debit
    balanceBefore: BigDecimal @NotNull;
    balanceAfter: BigDecimal @NotNull;
    
    type: String @NotNull @EnumType(TransactionType);
    reason: String @NotNull @Length(max = 500);
    
    paymentProvider: String @Nullable @Length(max = 50); // stripe, crypto, etc.
    externalTxId: String @Nullable @Length(max = 128);
    
    timestamp: Instant @NotNull @Default(Instant.now());
    
    @Index(name = "idx_balance_tx_user_time", columns = [userId, timestamp])
    @Index(name = "idx_balance_tx_type", columns = [type])
    @Index(name = "idx_balance_tx_order", columns = [orderId])
  }

  // ============================================================
  // PROXY INFRASTRUCTURE ENTITIES
  // ============================================================
  
  @Table(name = "proxy_nodes")
  entity ProxyNode {
    @Generated(UUID)
    id: UUID @NotNull;
    
    provider: String @NotNull @EnumType(ProxyProvider);
    providerInstanceId: String @Nullable @Length(max = 64);
    
    publicIp: String @NotNull @Length(max = 45); // IPv4 or IPv6
    port: Int @NotNull @Range(min = 1, max = 65535);
    
    region: String @NotNull @Length(max = 50); // e.g., "us-east-1", "eu-central-1"
    country: String @NotNull @Length(max = 2); // ISO 3166-1 alpha-2
    city: String @Nullable @Length(max = 100);
    
    tier: String @NotNull @EnumType(ProxyTier);
    
    capacity: Int @NotNull @Default(100) @Range(min = 1, max = 10000);
    currentLoad: Int @NotNull @Default(0);
    
    costPerHour: BigDecimal @NotNull @Default(0.00);
    
    authUsername: String @Nullable @Length(max = 64);
    authPassword: String @Nullable @Length(max = 128);
    
    registeredAt: Instant @NotNull @Default(Instant.now());
    lastHealthcheck: Optional<Instant>;
    status: String @NotNull @EnumType(ProxyStatus) @Default(ProxyStatus.ONLINE);
    
    tags: List<String> @Nullable; // JSON array for flexible categorization
    
    @Index(name = "idx_proxy_nodes_tier_status", columns = [tier, status])
    @Index(name = "idx_proxy_nodes_region_tier", columns = [region, tier])
    @Index(name = "idx_proxy_nodes_provider", columns = [provider])
    @Index(name = "idx_proxy_nodes_ip", columns = [publicIp], unique = true)
  }
  
  @Table(name = "proxy_metrics")
  entity ProxyMetrics {
    @Generated(UUID)
    id: UUID @NotNull;
    
    proxyNodeId: UUID @NotNull @Indexed;
    
    // Request statistics (rolling 1h window)
    totalRequests: Long @NotNull @Default(0);
    successfulRequests: Long @NotNull @Default(0);
    failedRequests: Long @NotNull @Default(0);
    
    // Calculated rates (0.0 to 1.0)
    successRate: Double @NotNull @Default(1.0) @Range(min = 0.0, max = 1.0);
    banRate: Double @NotNull @Default(0.0) @Range(min = 0.0, max = 1.0);
    
    // Latency metrics (milliseconds)
    latencyP50: Int @NotNull @Default(0);
    latencyP95: Int @NotNull @Default(0);
    latencyP99: Int @NotNull @Default(0);
    
    activeConnections: Int @NotNull @Default(0);
    peakConnections: Int @NotNull @Default(0);
    
    // Error breakdown (JSON object)
    errorCodes: String @Nullable @Length(max = 1000); // JSON: {"401": 5, "403": 2, "429": 10}
    
    // Cost tracking
    bytesTransferred: Long @NotNull @Default(0);
    estimatedCost: BigDecimal @NotNull @Default(0.00);
    
    lastUpdated: Instant @NotNull @Default(Instant.now());
    windowStart: Instant @NotNull @Default(Instant.now());
    
    @Index(name = "idx_proxy_metrics_node", columns = [proxyNodeId])
    @Index(name = "idx_proxy_metrics_success_rate", columns = [successRate])
  }
  
  @Table(name = "device_nodes")
  entity DeviceNode {
    @Generated(UUID)
    id: UUID @NotNull;
    
    deviceType: String @NotNull @EnumType(DeviceType);
    
    osVersion: String @NotNull @Length(max = 50);
    deviceModel: String @Nullable @Length(max = 100);
    appVersion: String @Nullable @Length(max = 20);
    
    location: String @NotNull @Length(max = 100); // City, Country
    country: String @NotNull @Length(max = 2);
    timezone: String @NotNull @Length(max = 50);
    
    capacity: Int @NotNull @Default(10) @Range(min = 1, max = 100);
    activeSessions: Int @NotNull @Default(0);
    
    lastHeartbeat: Optional<Instant>;
    status: String @NotNull @EnumType(DeviceStatus) @Default(DeviceStatus.ACTIVE);
    
    fingerprint: String @Nullable @Length(max = 500); // Device fingerprint data
    
    registeredAt: Instant @NotNull @Default(Instant.now());
    retiredAt: Optional<Instant>;
    
    @Index(name = "idx_device_nodes_type_status", columns = [deviceType, status])
    @Index(name = "idx_device_nodes_country", columns = [country])
  }
  
  @Table(name = "tor_circuits")
  entity TorCircuit {
    @Generated(UUID)
    id: UUID @NotNull;
    
    circuitId: String @NotNull @Unique @Length(max = 64);
    
    entryNode: String @NotNull @Length(max = 100);
    middleNode: String @Nullable @Length(max = 100);
    exitNode: String @NotNull @Length(max = 100);
    exitGeo: String @NotNull @Length(max = 2); // Exit node country
    
    successRate: Double @NotNull @Default(1.0) @Range(min = 0.0, max = 1.0);
    latencyP95: Int @NotNull @Default(0);
    
    requestCount: Long @NotNull @Default(0);
    
    createdAt: Instant @NotNull @Default(Instant.now());
    expiresAt: Instant @NotNull;
    retiredAt: Optional<Instant>;
    
    status: String @NotNull @EnumType(CircuitStatus) @Default(CircuitStatus.ACTIVE);
    
    @Index(name = "idx_tor_circuits_status_geo", columns = [status, exitGeo])
    @Index(name = "idx_tor_circuits_expires", columns = [expiresAt])
  }

  // ============================================================
  // VALUE OBJECTS
  // ============================================================
  
  @ValueObject
  value Money {
    amount: BigDecimal @NotNull;
    currency: String @NotNull @Default("USD") @Length(max = 3);
  }
  
  @ValueObject
  value GeoLocation {
    country: String @NotNull @Length(max = 2);
    region: String @Nullable @Length(max = 50);
    city: String @Nullable @Length(max = 100);
    latitude: Double @Nullable;
    longitude: Double @Nullable;
  }
  
  @ValueObject
  value TimeRange {
    start: Instant @NotNull;
    end: Instant @NotNull;
  }

  // ============================================================
  // AGGREGATE DEFINITIONS
  // ============================================================
  
  @AggregateRoot
  aggregate OrderAggregate {
    root: Order;
    entities: [BalanceTransaction];
    
    invariants {
      invariant PositiveQuantity: "order.quantity > 0";
      invariant DeliveredNotExceedQuantity: "order.delivered <= order.quantity";
      invariant CostMatchesQuantity: "order.cost >= (order.quantity / 1000) * service.costPer1k";
    }
  }
  
  @AggregateRoot
  aggregate ProxyPoolAggregate {
    root: ProxyNode;
    entities: [ProxyMetrics];
    
    invariants {
      invariant LoadNotExceedCapacity: "proxyNode.currentLoad <= proxyNode.capacity";
      invariant SuccessRateInRange: "proxyMetrics.successRate >= 0.0 && proxyMetrics.successRate <= 1.0";
    }
  }

  // ============================================================
  // DTO DEFINITIONS (API Contracts)
  // ============================================================
  
  @Dto(entity = Order)
  dto CreateOrderRequest {
    serviceId: UUID @NotNull;
    quantity: Int @NotNull @Range(min = 1, max = 10000000);
    targetUrl: String @NotNull @Length(max = 512);
    geoProfile: String @Nullable @EnumType(GeoProfile);
    speedMultiplier: Double @Nullable @Range(min = 0.1, max = 5.0);
  }
  
  @Dto(entity = Order)
  dto OrderResponse {
    id: UUID @NotNull;
    serviceId: UUID @NotNull;
    serviceName: String @NotNull;
    quantity: Int @NotNull;
    delivered: Int @NotNull;
    targetUrl: String @NotNull;
    geoProfile: String @NotNull;
    status: String @NotNull;
    cost: BigDecimal @NotNull;
    refundAmount: BigDecimal @NotNull;
    startCount: Optional<Int>;
    currentCount: Optional<Int>;
    createdAt: Instant @NotNull;
    startedAt: Optional<Instant>;
    completedAt: Optional<Instant>;
    failureReason: String @Nullable;
  }
  
  @Dto(entity = Order)
  dto OrderStatusUpdate {
    orderId: UUID @NotNull;
    status: String @NotNull @EnumType(OrderStatus);
    delivered: Int @NotNull;
    currentCount: Optional<Int>;
    failureReason: String @Nullable;
  }
  
  @Dto(entity = User)
  dto UserRegistrationRequest {
    email: String @NotNull @Length(max = 255);
    password: String @NotNull @Length(max = 128);
    referralCode: String @Nullable @Length(max = 32);
  }
  
  @Dto(entity = User)
  dto UserProfileResponse {
    id: UUID @NotNull;
    email: String @NotNull;
    tier: String @NotNull;
    balance: BigDecimal @NotNull;
    apiKey: String @Nullable;
    webhookUrl: String @Nullable;
    createdAt: Instant @NotNull;
    lastLogin: Optional<Instant>;
    status: String @NotNull;
    emailVerified: Boolean @NotNull;
    twoFactorEnabled: Boolean @NotNull;
  }
  
  @Dto(entity = BalanceTransaction)
  dto DepositRequest {
    amount: BigDecimal @NotNull @Range(min = 5.00, max = 10000.00);
    paymentProvider: String @NotNull @Length(max = 50);
  }
  
  @Dto(entity = BalanceTransaction)
  dto TransactionHistoryItem {
    id: UUID @NotNull;
    amount: BigDecimal @NotNull;
    type: String @NotNull;
    reason: String @NotNull;
    balanceAfter: BigDecimal @NotNull;
    timestamp: Instant @NotNull;
    orderId: Optional<UUID>;
  }
  
  @Dto(entity = ProxyNode)
  dto ProxyNodeStatus {
    id: UUID @NotNull;
    provider: String @NotNull;
    region: String @NotNull;
    tier: String @NotNull;
    status: String @NotNull;
    currentLoad: Int @NotNull;
    capacity: Int @NotNull;
    successRate: Double @NotNull;
    latencyP95: Int @NotNull;
    lastHealthcheck: Optional<Instant>;
  }
  
  @Dto(entity = Service)
  dto ServiceCatalogItem {
    id: UUID @NotNull;
    name: String @NotNull;
    displayName: String @NotNull;
    serviceType: String @NotNull;
    description: String @Nullable;
    costPer1k: BigDecimal @NotNull;
    minQuantity: Int @NotNull;
    maxQuantity: Int @NotNull;
    estimatedDaysMin: Int @NotNull;
    estimatedDaysMax: Int @NotNull;
    geoProfiles: List<String> @NotNull;
  }

  // ============================================================
  // REPOSITORY DEFINITIONS
  // ============================================================
  
  repository UserRepository for User {
    Optional<User> findByEmail(email: String);
    Optional<User> findByApiKey(apiKey: String);
    List<User> findByTier(tier: String);
    List<User> findByStatus(status: String);
    List<User> findByReferredBy(referredBy: UUID);
    Long countByTierAndStatus(tier: String, status: String);
  }
  
  repository OrderRepository for Order {
    List<Order> findByUserId(userId: UUID);
    List<Order> findByUserIdAndStatus(userId: UUID, status: String);
    List<Order> findByStatus(status: String);
    List<Order> findByStatusIn(statuses: List<String>);
    List<Order> findByCreatedAtBetween(start: Instant, end: Instant);
    Long countByUserIdAndStatus(userId: UUID, status: String);
    Long countByStatus(status: String);
  }
  
  repository ServiceRepository for Service {
    Optional<Service> findByName(name: String);
    List<Service> findByServiceType(serviceType: String);
    List<Service> findByIsActiveOrderBySortOrder(isActive: Boolean);
  }
  
  repository BalanceTransactionRepository for BalanceTransaction {
    List<BalanceTransaction> findByUserId(userId: UUID);
    List<BalanceTransaction> findByUserIdOrderByTimestampDesc(userId: UUID);
    List<BalanceTransaction> findByOrderId(orderId: UUID);
    List<BalanceTransaction> findByTypeAndTimestampBetween(type: String, start: Instant, end: Instant);
  }
  
  repository ProxyNodeRepository for ProxyNode {
    List<ProxyNode> findByTierAndStatus(tier: String, status: String);
    List<ProxyNode> findByProviderAndStatus(provider: String, status: String);
    List<ProxyNode> findByRegionAndTierAndStatus(region: String, tier: String, status: String);
    List<ProxyNode> findByCountryAndTierAndStatus(country: String, tier: String, status: String);
    Optional<ProxyNode> findByPublicIp(publicIp: String);
    Long countByTierAndStatus(tier: String, status: String);
  }
  
  repository ProxyMetricsRepository for ProxyMetrics {
    Optional<ProxyMetrics> findByProxyNodeId(proxyNodeId: UUID);
    List<ProxyMetrics> findBySuccessRateGreaterThanOrderBySuccessRateDesc(minSuccessRate: Double);
    List<ProxyMetrics> findByLastUpdatedBefore(cutoff: Instant);
  }
  
  repository DeviceNodeRepository for DeviceNode {
    List<DeviceNode> findByDeviceTypeAndStatus(deviceType: String, status: String);
    List<DeviceNode> findByCountryAndStatus(country: String, status: String);
    Long countByDeviceTypeAndStatus(deviceType: String, status: String);
  }
  
  repository TorCircuitRepository for TorCircuit {
    Optional<TorCircuit> findByCircuitId(circuitId: String);
    List<TorCircuit> findByStatusAndExitGeo(status: String, exitGeo: String);
    List<TorCircuit> findByStatusOrderBySuccessRateDesc(status: String);
    List<TorCircuit> findByExpiresAtBefore(cutoff: Instant);
  }

  // ============================================================
  // SERVICE DEFINITIONS
  // ============================================================
  
  service OrderService {
    @Api(description = "Creates a new order and deducts balance")
    OrderResponse createOrder(request: CreateOrderRequest, userId: UUID)
      throws InsufficientBalanceException, InvalidServiceException;
    
    @Api(description = "Updates order progress")
    OrderResponse updateOrderProgress(orderId: UUID, delivered: Int, currentCount: Optional<Int>);
    
    @Api(description = "Marks order as completed")
    OrderResponse completeOrder(orderId: UUID);
    
    @Api(description = "Fails an order with reason")
    OrderResponse failOrder(orderId: UUID, reason: String);
    
    @Api(description = "Processes refund for failed order")
    OrderResponse refundOrder(orderId: UUID, refundAmount: BigDecimal);
  }
  
  service ProxySelectionService {
    @Api(description = "Selects best proxy for operation")
    Optional<ProxyNodeStatus> selectProxy(operation: String, targetCountry: String, quantity: Int);
    
    @Api(description = "Reports proxy usage result")
    Void reportProxyResult(proxyId: UUID, success: Boolean, latencyMs: Int, errorCode: Optional<Int>);
    
    @Api(description = "Gets proxy pool health status")
    List<ProxyNodeStatus> getPoolHealth(tier: String);
  }
  
  service BalanceService {
    @Api(description = "Adds funds to user balance")
    TransactionHistoryItem deposit(userId: UUID, request: DepositRequest);
    
    @Api(description = "Deducts funds for order")
    TransactionHistoryItem debitForOrder(userId: UUID, orderId: UUID, amount: BigDecimal);
    
    @Api(description = "Refunds amount to user")
    TransactionHistoryItem refund(userId: UUID, orderId: UUID, amount: BigDecimal, reason: String);
    
    @Api(description = "Gets transaction history")
    List<TransactionHistoryItem> getHistory(userId: UUID, limit: Int);
  }

}
