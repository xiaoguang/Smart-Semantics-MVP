package com.example;

class DepotHeadService {
  private final AuditClient auditClient = null;

  void batchSetStatus(String status) {
    auditClient.recordStatus(null);
  }
}
