package com.example;

interface DepotHeadMapper {
  void updateStatus(String status);
}

interface AuditClient {
  boolean recordStatus(int status);
}
