package org.sourceanalysis.tools.jdtsyntax;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Strict one-request-at-a-time JSONL entry point for the standalone helper process. */
public final class JdtSyntaxHelperMain {

  private JdtSyntaxHelperMain() {}

  public static void main(String[] args) throws Exception {
    ObjectMapper mapper =
        new ObjectMapper(
                JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    JdtSyntaxReader reader = new JdtSyntaxReader();
    try (BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter output =
            new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        JdtSyntaxProtocol.Request request = mapper.readValue(line, JdtSyntaxProtocol.Request.class);
        JdtSyntaxProtocol.Response response = reader.describe(request);
        output.write(mapper.writeValueAsString(response));
        output.newLine();
        output.flush();
      }
    }
  }
}
