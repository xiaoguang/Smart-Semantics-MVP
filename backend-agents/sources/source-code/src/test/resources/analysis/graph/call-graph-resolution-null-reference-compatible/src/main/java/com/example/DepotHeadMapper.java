package com.example;

interface DepotHeadMapper {
  void updateStatus(String status);
}

interface AuditClient {
  boolean recordStatus(String status);
  boolean recordStatus(int status);
}
