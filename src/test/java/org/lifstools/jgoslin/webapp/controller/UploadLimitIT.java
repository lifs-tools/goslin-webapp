package org.lifstools.jgoslin.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A file over spring.servlet.multipart.max-file-size, posted without JavaScript (with JavaScript the page refuses
 * it before sending), is answered with 413 by Spring's default exception resolver. The body is empty: the error
 * dispatch parses the multipart request again and fails again. No controller's exception handler is involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = {"test", "dev"})
class UploadLimitIT {

    @Value("${local.server.port}")
    private int port;

    @Test
    void oversizeFileGivesTheErrorPage() throws Exception {
        String boundary = "goslin-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"names.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write("PC 32:0\n".repeat(80_000).getBytes(StandardCharsets.US_ASCII)); // 640 KB > 512 KB
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/goslin/validatefile"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
    }
}
