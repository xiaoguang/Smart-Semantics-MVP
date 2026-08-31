package example.inventory;

import org.springframework.stereotype.Service;

@Service
final class ReservationService {
  private final InventoryMapper mapper;
  ReservationService(InventoryMapper mapper) { this.mapper = mapper; }
  ReservationReceipt reserve(String sku, int quantity) {
    if (quantity <= 0) throw new InvalidQuantity();
    InventoryRow inventory = mapper.findBySku(sku);
    int available = inventory.onHand() - inventory.reserved();
    if (available < quantity) throw new InsufficientInventory();
    int updateCount = mapper.addReservation(sku, quantity, inventory.version());
    if (updateCount != 1) throw new ConcurrentInventoryChange();
    return new ReservationReceipt(sku, quantity);
  }
}
record InventoryRow(String sku, int onHand, int reserved, int version) {}
record ReservationReceipt(String sku, int quantity) {}
final class InvalidQuantity extends RuntimeException {}
final class InsufficientInventory extends RuntimeException {}
final class ConcurrentInventoryChange extends RuntimeException {}
