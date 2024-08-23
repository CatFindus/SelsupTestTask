import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class CrptApi {
    private final static String DOC_TYPE = "LP_INTRODUCE_GOODS";
    private final static String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final int requestLimit;
    private final long millisInterval;
    private final Semaphore semaphore;
    private final ScheduledExecutorService executorService;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ReentrantLock lock;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        lock = new ReentrantLock();
        this.requestLimit = requestLimit;
        millisInterval = timeUnit.toMillis(1L);
        semaphore = new Semaphore(requestLimit);
        executorService = Executors.newScheduledThreadPool(1);
        httpClient = HttpClient.newHttpClient();
        mapper = new ObjectMapper();

        executorService.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } finally {
                lock.unlock();
            }
        }, millisInterval, millisInterval, TimeUnit.MILLISECONDS);
    }


    //    The return value and response processing are given as an example and should be implemented depending on the operating conditions of the real API
    public boolean createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();

        String jsonBody = createJsonBody(document, signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200 || response.statusCode() == 201;
    }

    public void shutdown() {
        executorService.shutdown();
    }

    private String createJsonBody(Document document, String signature) throws JsonProcessingException {
        DocumentRequest request = new DocumentRequest(document, signature);
        return mapper.writeValueAsString(request);
    }

    @RequiredArgsConstructor
    @Builder
    @Getter
    private static class DocumentRequest {
        private final Document description;
        private final String signature;
    }

    @RequiredArgsConstructor
    @Builder
    @Getter
    public static class Document {
        private final String participantInn;
        private final String doc_id;
        private final String doc_status;
        private final String doc_type = DOC_TYPE;
        private final boolean importRequest;
        private final String owner_inn;
        private final String producer_inn;
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private final LocalDate production_date;
        private final String production_type;
        private final Product[] products;
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private final LocalDate reg_date;
        private final String reg_number;

        @RequiredArgsConstructor
        @Builder
        @Getter
        public static class Product {
            private final String certificate_document;
            @JsonSerialize(using = LocalDateSerializer.class)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            private final LocalDate certificate_document_date;
            private final String certificate_document_number;
            private final String owner_inn;
            private final String producer_inn;
            @JsonSerialize(using = LocalDateSerializer.class)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            private final LocalDate production_date;
            private final String tnved_code;
            private final String uit_code;
            private final String uitu_code;
        }
    }
}
