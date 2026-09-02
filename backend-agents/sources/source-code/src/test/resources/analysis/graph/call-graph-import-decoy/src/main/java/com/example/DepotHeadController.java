package com.example;

import com.decoy.DepotHeadService;

class DepotHeadController {
  private final DepotHeadService depotHeadService = new DepotHeadService();

  void batchSetStatus(String status) {
    depotHeadService.batchSetStatus(status);
  }
}
