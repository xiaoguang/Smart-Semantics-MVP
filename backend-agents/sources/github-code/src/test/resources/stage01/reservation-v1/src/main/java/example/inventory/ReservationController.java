package example.inventory;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
final class ReservationController {
  private final ReservationService service;
  ReservationController(ReservationService service) { this.service = service; }
  @PostMapping
  ReservationReceipt reserve(@RequestBody ReservationRequest request) {
    return service.reserve(request.sku(), request.quantity());
  }
}
record ReservationRequest(String sku, int quantity) {}
