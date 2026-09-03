package com.example;

interface DepotHeadMapper {
  void updateStatus(String status);
}

interface AuditClient {
  boolean recordStatus(Integer status);
  boolean recordStatus(String status);
}
