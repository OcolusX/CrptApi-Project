package org.example;

import com.google.gson.Gson;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    // Клиент для выполнения http запросов
    private final OkHttpClient httpClient;

    // Объект для сериализации и десериализации JSON
    private final Gson gson;

    // Семафор для ограничения кол-ва одновременных запросов к API
    private final Semaphore semaphore;

    // Кол-вом доступных разрешений
    private final AtomicInteger permits;

    // main для вызова указанного в задании метода createDocument()
    public static void main(String[] args) {
        // Создание объекта CrptApi с ограничением в 10 запрос в минуту
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);

        // Создание объекта Document и подписи
        Document document = new Document();
        document.setDocId("123456");
        document.setDocStatus("draft");
        document.setDocType("LP_INTRODUCE_GOODS");
        document.setImportRequest(true);
        document.setOwnerInn("owner_inn_value");
        document.setParticipantInn("participant_inn_value");
        document.setProducerInn("producer_inn_value");
        document.setProductionDate("2020-01-23");
        document.setProductionType("production_type_value");
        document.setRegDate("2020-01-23");
        document.setRegNumber("reg_number_value");

        Product product = new Product();
        product.setCertificateDocument("certificate_document_value");
        product.setCertificateDocumentDate("2020-01-23");
        product.setCertificateDocumentNumber("certificate_document_number_value");
        product.setOwnerInn("owner_inn_value");
        product.setProducerInn("producer_inn_value");
        product.setProductionDate("2020-01-23");
        product.setTnvedCode("tnved_code_value");
        product.setUitCode("uit_code_value");
        product.setUituCode("uitu_code_value");

        document.setProducts(new CrptApi.Product[] {product});

        Description description = new Description();
        description.setParticipantInn("participant_inn_value");

        document.setDescription(description);

        String signature = "signature_value";

        ExecutorService executor = Executors.newFixedThreadPool(5); // Создаем пул из 5 потоков

        // Создаем 10 задач, каждая из которых выполняет вызов createDocument()
        for (int i = 0; i < 100; i++) {
            executor.execute(() -> crptApi.createDocument(document, signature));
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            // Поток main ждет завершения всех задач в пуле
        }

        System.out.println("Все вызовы createDocument() завершены.");
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.semaphore = new Semaphore(requestLimit);
        permits = new AtomicInteger(requestLimit);

        // Освобождаем достпуные разрешающие сигналы семафора раз в заданный интервал времени.
        // Поскольку задан лимит запросов в определённый интервал времени, то заводим планировщик задач.
        // Атомарная переменная permits необходима, чтобы контролировать кол-во сигналов, которое можно освободить
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::resetSemaphore, 1, 1, timeUnit);
    }

    // Освобождает все разрешающие сигналы семафора, что позволяет новым потокам получать доступ к ресурсу
    private void resetSemaphore() {
        semaphore.release(permits.get());
        System.out.println("Освобождаем разрешающие сигналы");
    }

    public void createDocument(Document document, String signature) {
        try {
            // Пытаемся захватить разрешающий сигнал семафора и формируем тело запроса в формате JSON
            semaphore.acquire();
            permits.decrementAndGet(); // Уменьшаяем кол-во доступных разрешений
            String jsonBody = gson.toJson(document);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBody);

            // Формируем сам запрос
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .addHeader("Signature", signature)
                    .build();

            // Выполняем асинхронный запрос
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    permits.incrementAndGet();  // Увеличиваем кол-во доступных разрешений
                    System.out.println(e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    permits.incrementAndGet();  // Увеличиваем кол-во доступных разрешений
                    assert response.body() != null;
                    System.out.println(response.body().string());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(e.getMessage());
        }
    }

    public static class Document {

        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public Product[] getProducts() {
            return products;
        }

        public void setProducts(Product[] products) {
            this.products = products;
        }

        public String getRegDate() {
            return regDate;
        }

        public void setRegDate(String regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public String getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(String certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }

    public static class Description {
        private String participantInn;

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }
}
