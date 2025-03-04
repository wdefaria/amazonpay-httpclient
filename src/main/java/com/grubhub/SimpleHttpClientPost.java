package com.grubhub;

import com.amazon.pay.api.PayConfiguration;
import com.amazon.pay.api.RequestSigner;
import com.amazon.pay.api.types.Environment;
import com.amazon.pay.api.types.Region;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleHttpClientPost {

    private final HttpClient httpClient;

    public SimpleHttpClientPost() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public String sendPostRequest(String url, Map<String, String> headers, String body, Map<String, List<String>> queryParams) throws IOException, InterruptedException {
        if (queryParams != null && !queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("&"));
            url += "?" + queryString;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to send request. Status code: " + response.statusCode() + " Error: " + response.body());
        }

        return response.body();
    }

    public static void printCurlCommand(String url, Map<String, String> headers, String body, Map<String, List<String>> queryParams) {
        if (queryParams != null && !queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("&"));
            url += "?" + queryString;
        }

        StringBuilder curlCommand = new StringBuilder("curl -X POST ");
        curlCommand.append("\"").append(url).append("\" ");

        if (headers != null) {
            headers.forEach((key, value) -> curlCommand.append("-H \"").append(key).append(": ").append(value).append("\" "));
        }

        curlCommand.append("-d '").append(body).append("'");

        System.out.println(curlCommand.toString());
    }

    public static void main(String[] args) throws Exception {

        String requestId = UUID.randomUUID().toString();
        String privateKeyPath = "/Users/wdefaria/Documents/amazonPayKeys/privateKey.pem";

        String buyerId = "amzn1.account.AFHHMTSZZHMIZJYK4U6YGJ4VEEHQ";
        var primeBenefitURI = "https://prime-apis.amazon.com/v1/customer/link-benefit/PRIME_GRUB_HUB_BENEFIT";

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, String> header = Map.of("x-amz-request-id", requestId, "x-amz-marketplace", "US", "x-amz-pay-idempotency-key", idempotencyKey);
        Map<String, List<String>> queryParams = Map.of("customerIdentifier", List.of(buyerId), "customerIdentifierType", List.of("ApayBuyerId"));

        //Object test
        AmazonBenefitsStatusMessage message = new AmazonBenefitsStatusMessage("Active", buyerId, "ONLINE_EMBEDDED");
        ObjectMapper objectMapper = new ObjectMapper();
        String payload = objectMapper.writeValueAsString(message);
        System.out.println(payload);

        char[] privateKeyChars = readPemFile(privateKeyPath);

        PayConfiguration payConfiguration = new PayConfiguration()
                .setPrivateKey(privateKeyChars)
                .setPublicKeyId("SANDBOX-AEE2ZD325IKWE7JIRDGJ3EHP")
                .setEnvironment(Environment.SANDBOX)
                .setRegion(Region.NA);


        RequestSigner requestSigner = new RequestSigner(payConfiguration);
        Map<String, String> signedRequest = requestSigner.signRequest(URI.create(primeBenefitURI), "POST", queryParams, payload, header);
        System.out.println(signedRequest);


        SimpleHttpClientPost client = new SimpleHttpClientPost();

        try {
            printCurlCommand(primeBenefitURI, signedRequest, payload, queryParams);
            String response = client.sendPostRequest(primeBenefitURI, signedRequest, payload, queryParams);
            System.out.println("Response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static char[] readPemFile(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        String pemContent = new String(fileBytes);

        return pemContent.toCharArray();
    }

    private static String readPrivateKeyWithNewLines(String filePath) throws IOException {
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            return lines
                    .map(line -> line.replace("\n", "").replace("\r", "")) // Remove raw newlines
                    .collect(Collectors.joining("\\n")); // Add `\n` as a literal escape sequence
        }
    }
}

@Data
@NoArgsConstructor
@Builder(toBuilder = true)
class AmazonBenefitsStatusMessage {
    @JsonProperty("benefitsStatus")
    private String benefitsStatus;

    /**
     * This value indicates the customerId on client side that ellis can use for any
     * future communications such as notification of change of Customer state etc.
     */
    @JsonProperty("clientCustomerId")
    private String clientCustomerId;

    /**
     * This value indicates the link channel from where the customer data was
     * acquired by the client:
     * Enum value: Online / Offline
     */
    @JsonProperty("linkChannel")
    private String linkChannel;

    @JsonCreator
    public AmazonBenefitsStatusMessage(@JsonProperty("benefitsStatus") String benefitsStatus,
                                       @JsonProperty("clientCustomerId") String clientCustomerId,
                                       @JsonProperty("linkChannel") String linkChannel) {
        this.benefitsStatus = benefitsStatus;
        this.clientCustomerId = clientCustomerId;
        this.linkChannel = linkChannel;
    }
}
