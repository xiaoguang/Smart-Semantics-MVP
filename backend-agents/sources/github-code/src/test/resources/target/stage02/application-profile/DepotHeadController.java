package example.depot;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/depotHead")
final class DepotHeadController {
  @PostMapping("/batchSetStatus")
  String batchSetStatus(String status, String ids) {
    return "ok";
  }
}
