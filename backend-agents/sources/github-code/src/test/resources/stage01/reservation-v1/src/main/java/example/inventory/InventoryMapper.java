package example.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface InventoryMapper {
  InventoryRow findBySku(@Param("sku") String sku);
  int addReservation(@Param("sku") String sku,
                     @Param("quantity") int quantity,
                     @Param("version") int version);
}
