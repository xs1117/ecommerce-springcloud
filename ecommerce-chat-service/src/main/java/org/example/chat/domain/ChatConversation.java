package org.example.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversation")
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_key", nullable = false, unique = true, length = 128)
    private String conversationKey;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "store_title", nullable = false, length = 200)
    private String storeTitle;

    @Column(name = "store_image_url")
    private String storeImageUrl;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "buyer_username", nullable = false, length = 100)
    private String buyerUsername;

    @Column(name = "buyer_nickname", nullable = false, length = 100)
    private String buyerNickname;

    @Column(name = "merchant_user_id", nullable = false)
    private Long merchantUserId;

    @Column(name = "merchant_username", nullable = false, length = 100)
    private String merchantUsername;

    @Column(name = "merchant_nickname", nullable = false, length = 100)
    private String merchantNickname;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType = "SHOP_SUPPORT";

    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "after_sale_status", length = 32)
    private String afterSaleStatus;

    @Column(name = "admin_joined", nullable = false)
    private Boolean adminJoined = false;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "admin_nickname", length = 100)
    private String adminNickname;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_title", length = 200)
    private String productTitle;

    @Column(name = "product_image_url")
    private String productImageUrl;

    @Column(name = "product_description", columnDefinition = "text")
    private String productDescription;

    @Column(name = "product_price", precision = 18, scale = 2)
    private BigDecimal productPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_message_type", nullable = false, length = 32)
    private MessageType lastMessageType = MessageType.TEXT;

    @Column(name = "last_message_text", nullable = false, length = 500)
    private String lastMessageText = "";

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "buyer_unread_count", nullable = false)
    private Integer buyerUnreadCount = 0;

    @Column(name = "merchant_unread_count", nullable = false)
    private Integer merchantUnreadCount = 0;

    @Column(name = "admin_unread_count", nullable = false)
    private Integer adminUnreadCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (lastMessageAt == null) {
            lastMessageAt = now;
        }
        if (status == null) {
            status = ConversationStatus.ACTIVE;
        }
        if (bizType == null) {
            bizType = "SHOP_SUPPORT";
        }
        if (adminJoined == null) {
            adminJoined = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(String conversationKey) {
        this.conversationKey = conversationKey;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getStoreTitle() {
        return storeTitle;
    }

    public void setStoreTitle(String storeTitle) {
        this.storeTitle = storeTitle;
    }

    public String getStoreImageUrl() {
        return storeImageUrl;
    }

    public void setStoreImageUrl(String storeImageUrl) {
        this.storeImageUrl = storeImageUrl;
    }

    public Long getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(Long buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public String getBuyerUsername() {
        return buyerUsername;
    }

    public void setBuyerUsername(String buyerUsername) {
        this.buyerUsername = buyerUsername;
    }

    public String getBuyerNickname() {
        return buyerNickname;
    }

    public void setBuyerNickname(String buyerNickname) {
        this.buyerNickname = buyerNickname;
    }

    public Long getMerchantUserId() {
        return merchantUserId;
    }

    public void setMerchantUserId(Long merchantUserId) {
        this.merchantUserId = merchantUserId;
    }

    public String getMerchantUsername() {
        return merchantUsername;
    }

    public void setMerchantUsername(String merchantUsername) {
        this.merchantUsername = merchantUsername;
    }

    public String getMerchantNickname() {
        return merchantNickname;
    }

    public void setMerchantNickname(String merchantNickname) {
        this.merchantNickname = merchantNickname;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getAfterSaleStatus() {
        return afterSaleStatus;
    }

    public void setAfterSaleStatus(String afterSaleStatus) {
        this.afterSaleStatus = afterSaleStatus;
    }

    public Boolean getAdminJoined() {
        return adminJoined;
    }

    public void setAdminJoined(Boolean adminJoined) {
        this.adminJoined = adminJoined;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAdminNickname() {
        return adminNickname;
    }

    public void setAdminNickname(String adminNickname) {
        this.adminNickname = adminNickname;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getProductImageUrl() {
        return productImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        this.productImageUrl = productImageUrl;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public MessageType getLastMessageType() {
        return lastMessageType;
    }

    public void setLastMessageType(MessageType lastMessageType) {
        this.lastMessageType = lastMessageType;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Integer getBuyerUnreadCount() {
        return buyerUnreadCount;
    }

    public void setBuyerUnreadCount(Integer buyerUnreadCount) {
        this.buyerUnreadCount = buyerUnreadCount;
    }

    public Integer getMerchantUnreadCount() {
        return merchantUnreadCount;
    }

    public void setMerchantUnreadCount(Integer merchantUnreadCount) {
        this.merchantUnreadCount = merchantUnreadCount;
    }

    public Integer getAdminUnreadCount() {
        return adminUnreadCount;
    }

    public void setAdminUnreadCount(Integer adminUnreadCount) {
        this.adminUnreadCount = adminUnreadCount;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

