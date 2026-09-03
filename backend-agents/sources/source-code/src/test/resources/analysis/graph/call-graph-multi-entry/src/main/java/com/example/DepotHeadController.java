package com.example;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/depotHead")
class DepotHeadController {
  private final DepotHeadService depotHeadService = new DepotHeadService();

  @PostMapping("/batchSetStatus")
  void batchSetStatus(String status) {
    depotHeadService.batchSetStatus(status);
  }

  @PostMapping("/batchSetStatus/retry")
  void retryBatchSetStatus(String status) {
    depotHeadService.batchSetStatus(status);
  }
}
