package com.example;

public class DepotHeadController {
  public String batchSetStatus(String status, String ids) {
    return new DepotHeadService().batchSetStatus(status, ids);
  }
}
