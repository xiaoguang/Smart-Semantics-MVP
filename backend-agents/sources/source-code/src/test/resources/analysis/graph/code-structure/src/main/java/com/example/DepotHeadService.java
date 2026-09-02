package com.example;

public class DepotHeadService {
  public String batchSetStatus(String status, String ids) {
    DepotHead depotHead = new DepotHead();
    depotHead.setStatus(status);
    return new DepotHeadMapper().updateStatus(depotHead);
  }
}
