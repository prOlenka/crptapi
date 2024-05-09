package com.CrptApi;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrptApi {
	private final TimeUnit timeUnit;
	private final Semaphore semaphore;
	private final ScheduledExecutorService scheduler;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final String apiUrl;

	public CrptApi(TimeUnit timeUnit, int requestLimit, String apiUrl) {
		this.timeUnit = timeUnit;
		this.semaphore = new Semaphore(requestLimit);
		this.scheduler = Executors.newScheduledThreadPool(1);
		this.apiUrl = apiUrl;
	}

	public static void main(String[] args) throws InterruptedException {
		CrptApi api = new CrptApi(TimeUnit.SECONDS, 1, "https://ismp.crpt.ru/api/v3/lk/documents/create");
		final Logger logger = LogManager.getLogger(CrptApi.class);
		String json = "";
		try {
			byte[] bytes = Files.readAllBytes(Paths.get("src/main/resources/document.json"));
			json = new String(bytes, StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.error("An error occurred while reading the file", e);
		}

		Gson gson = new Gson();
		CrptApi.Document document = gson.fromJson(json, CrptApi.Document.class);

		String signature = "signature";
		api.createDocument(document, signature);

		api.shutdown();
	}


	public void createDocument(Document document, String signature) throws InterruptedException {
		semaphore.acquire();
		scheduler.scheduleAtFixedRate(semaphore::release, 1, 1, timeUnit);
		try {
			HttpRequest request = buildRequest(document, signature);
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				String responseBody = response.body();
			} else {
				System.out.println("Ошибка: " + response.statusCode());
			}
		}catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void shutdown() {
		scheduler.shutdown();
	}

	private HttpRequest buildRequest(Document document, String signature) {
		String json = convertToJson(document);
        return HttpRequest.newBuilder()
				.uri(URI.create(apiUrl))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
	}

	private String convertToJson(Document document) {
		Gson gson = new Gson();
		return gson.toJson(document);
	}

	@Getter
	@Setter
	private static class Document {
		private Description description;
		private String doc_id;
		private String doc_status;
		private String doc_type;
		private boolean importRequest;
		private String owner_inn;
		private String participant_inn;
		private String producer_inn;
		private String production_date;
		private String production_type;
		private List<Products> products;
		private String reg_date;
		private String reg_number;

	}

	@Setter
	@Getter
	public static class Description {
		private String participantInn;
	}

	@Setter
	@Getter
	public static class Products {
		private String certificate_document;
		private String certificate_document_date;
		private String certificate_document_number;
		private String owner_inn;
		private String producer_inn;
		private String production_date;
		private String tnved_code;
		private String uit_code;
		private String uitu_code;

	}
}
