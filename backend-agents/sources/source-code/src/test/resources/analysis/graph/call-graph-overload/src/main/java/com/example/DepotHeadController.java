package com.example;

class DepotHeadController {
  private final DepotHeadService depotHeadService = new DepotHeadService();

  void batchSetStatus(String status) {
    depotHeadService.batchSetStatus(status);
  }

  void batchSetStatus(Integer status) {}
}
