package example.other;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class OtherController {
  @GetMapping("/health")
  String health() {
    return "ok";
  }
}
