package org.example.inventory.controller;

import jakarta.validation.Valid;
import org.example.inventory.dto.AdjustStockRequest;
import org.example.inventory.dto.ConfirmRequest;
import org.example.inventory.dto.LockRequest;
import org.example.inventory.dto.ReleaseRequest;
import org.example.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/adjust")
    public ResponseEntity<?> adjust(@Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(request));
    }

    @PostMapping("/lock")
    public ResponseEntity<?> lock(@Valid @RequestBody LockRequest request) {
        return ResponseEntity.ok(inventoryService.lock(request));
    }

    @PostMapping("/release")
    public ResponseEntity<?> release(@Valid @RequestBody ReleaseRequest request) {
        return ResponseEntity.ok(inventoryService.release(request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(inventoryService.confirm(request));
    }

    @GetMapping("/skus")
    public ResponseEntity<?> skus() {
        return ResponseEntity.ok(inventoryService.listAll());
    }
}

