package com.example;

class DepotHeadService {
  private final DepotHeadMapper depotHeadMapper = null;

  void batchSetStatus(String status) {
    depotHeadMapper.updateStatus(status);
  }
}
