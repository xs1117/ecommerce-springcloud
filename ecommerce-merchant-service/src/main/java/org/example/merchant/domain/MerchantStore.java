package org.example.merchant.domain;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_store")
public class MerchantStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "store_name", nullable = false, length = 128)
    private String storeName;

    @Column(name = "store_intro", length = 255)
    private String storeIntro;

    @Column(name = "store_image_url", columnDefinition = "LONGTEXT")
    private String storeImageUrl;

    @Column(name = "main_category", length = 64)
    private String mainCategory;

    @Column(name = "tags", length = 255)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MerchantStoreStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getStoreIntro() { return storeIntro; }
    public void setStoreIntro(String storeIntro) { this.storeIntro = storeIntro; }
    public String getStoreImageUrl() { return storeImageUrl; }
    public void setStoreImageUrl(String storeImageUrl) { this.storeImageUrl = storeImageUrl; }
    public String getMainCategory() { return mainCategory; }
    public void setMainCategory(String mainCategory) { this.mainCategory = mainCategory; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public MerchantStoreStatus getStatus() { return status; }
    public void setStatus(MerchantStoreStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

