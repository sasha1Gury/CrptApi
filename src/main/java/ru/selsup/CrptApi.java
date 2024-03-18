package ru.selsup;

import com.google.gson.Gson;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final Gson gson;
    private final TimeUnit timeUnit;
    private LocalDateTime creationTime;
    private LocalDateTime lastPossibleTime;
    private final int requestLimit;
    private static int requestCount;
    private final HttpClient httpClient;
    private static final Object lock = new Object();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.creationTime = LocalDateTime.now();
        this.lastPossibleTime = creationTime.plus(1 , timeUnit.toChronoUnit());
        System.out.println(creationTime + "- create |||||||||| last - " + lastPossibleTime);
        gson = new Gson();
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String createJsonDocument(Document document) {
        return gson.toJson(document);
    }

    public synchronized void createDocumentOnServer(Document document, String signature) throws URISyntaxException, InterruptedException {
        String documentJson = createJsonDocument(document);
        synchronized (lock) {
            LocalDateTime lastTimeConnection = LocalDateTime.now();
            CrptApi.requestCount += 1;
            if (CrptApi.requestCount <= requestLimit) {
                sendRequest(documentJson, signature);
            } else {
                long waitTime = Duration.between(lastTimeConnection, lastPossibleTime).toMillis();
                if (waitTime > 0) {
                    wait(waitTime);
                }
                CrptApi.requestCount = 0;
                creationTime = creationTime.plus(1, timeUnit.toChronoUnit());
                lastPossibleTime = creationTime.plus(1, timeUnit.toChronoUnit());
                System.out.println("\n\nafter wait\n\n");
                System.out.println(creationTime + "- create |||||||||| last - " + lastPossibleTime);
                sendRequest(documentJson, signature);
            }
        }
        notifyAll();
    }

    public void sendRequest(String documentJson, String signature) throws URISyntaxException {
        synchronized (lock) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(documentJson))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("Response code: " + response.statusCode());
                System.out.println("Response body: " + response.body());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Data
    private static class Document {
        Description description;
        String doc_id;
        String doc_status;
        String doc_type;
        boolean importRequest;
        String owner_inn;
        String participant_inn;
        String producer_inn;
        String production_date;
        String production_type;
        Product[] products;
        String reg_date;
        String reg_number;
    }

    @Data
    private static class Description {
        String participantInn;
    }

    @Data
    private static class Product {
        String certificate_document;
        String certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        String production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
    }

    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);
        Gson gson = new Gson();
        Document document = gson.fromJson("{\"description\": { \"participantInn\": \"string\" }, \"doc_id\": \"string\"," +
                " \"doc_status\": \"string\", \"doc_type\": \"LP_INTRODUCE_GOODS\", \"importRequest\": true, \"owner_inn\":" +
                " \"string\", \"participant_inn\": \"string\", \"producer_inn\": \"string\", \"production_date\": \"2020-01-23\"," +
                " \"production_type\": \"string\", \"products\": [ { \"certificate_document\": \"string\", \"certificate_document_date\":" +
                " \"2020-01-23\", \"certificate_document_number\": \"string\", \"owner_inn\": \"string\", \"producer_inn\": \"string\"," +
                " \"production_date\": \"2020-01-23\", \"tnved_code\": \"string\", \"uit_code\": \"string\", \"uitu_code\": \"string\" } ]" +
                ", \"reg_date\": \"2020-01-23\", \"reg_number\": \"string\"}", Document.class);
        for (int i = 0; i < 20; i++) {
            crptApi.createDocumentOnServer(document, "123");
        }
    }
}