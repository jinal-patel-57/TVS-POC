package com.tvsmotor.migration.utility.portlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.liferay.portal.kernel.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class LiferayContentMigrationUtil {

    private static final ObjectMapper _mapper = new ObjectMapper();

    /**
     * Creates a structured content (web content) in Liferay using headless-admin-content API.
     *
     * @param inputJsonString JSON string (your Sitecore-like JSON)
     * @param siteId          groupId/site id
     * @param contentStructureId  content structure id or key (adjust per your instance)
     * @param baseUrl         base url of Liferay instance (e.g. http://localhost:8080)
     * @param authHeaderValue Authorization header value (e.g. "Basic base64" or "Bearer <token>")
     * @throws Exception
     */
    public static void createStructuredContentFromJson(
            JSONObject inputJson,
            long siteId,
            String contentStructureId,
            String baseUrl,
            String authHeaderValue
    ) throws Exception {

    	 // Convert Liferay JSONObject → Jackson JsonNode
        JsonNode root = _mapper.readTree(inputJson.toString());

        // ---------- TITLE & DESCRIPTION -----------
        String pageTitle = safeText(root, "/fields/pageTitle");
        String metaDescription = safeText(root, "/fields/metaDescription");

        ObjectNode titleNode = _mapper.createObjectNode();
        titleNode.put("en_US", pageTitle != null ? pageTitle : "Untitled");

        ObjectNode descriptionNode = _mapper.createObjectNode();
        descriptionNode.put("en_US", metaDescription != null ? metaDescription : "");

        // ---------- HERO ITEMS -----------
        JsonNode mainArr = root.path("layout").path("placeholders").path("main");

        ArrayNode itemsArray = null;
        if (mainArr.isArray()) {
            for (JsonNode comp : mainArr) {
                if ("Hero".equals(comp.path("component").asText())) {
                    itemsArray = (ArrayNode) comp.path("data").path("items");
                    break;
                }
            }
        }

        if (itemsArray == null) itemsArray = _mapper.createArrayNode();

        // Build repeatable entries for Fieldset70304617
        ArrayNode repeatableEntries = _mapper.createArrayNode();

        for (JsonNode item : itemsArray) {
            ObjectNode entry = _mapper.createObjectNode();

            // ---- Text98594310 ----
            String link = item.path("link").asText("");
            ObjectNode linkNode = _mapper.createObjectNode();
            linkNode.put("en_US", link);
            entry.set("Text98594310", linkNode);

            repeatableEntries.add(entry);
        }

        // ---------- contentFields wrapper -----------
        ArrayNode contentFields = _mapper.createArrayNode();
        ObjectNode fieldsetNode = _mapper.createObjectNode();
        fieldsetNode.put("name", "Fieldset70304617");   // from your structure JSON
        fieldsetNode.set("value", repeatableEntries);
        contentFields.add(fieldsetNode);

        // ---------- Final payload -----------
        ObjectNode payload = _mapper.createObjectNode();
        payload.set("title", titleNode);
        payload.set("description", descriptionNode);

        payload.put("contentStructureId", contentStructureId);
        payload.put("contentStructureKey", contentStructureId);

        payload.set("contentFields", contentFields);

        // ---------- HTTP call -----------
        String endpoint = baseUrl + "/o/headless-admin-content/v1.0/sites/" + siteId + "/structured-contents/draft";
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authHeaderValue)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("User-Agent", "Liferay-Migration-Client/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        System.out.println("=== REQUEST ===");
        System.out.println("URI: " + request.uri());
        System.out.println("Method: POST");
        request.headers().map().forEach((k,v)-> System.out.println(k + ": " + v));
        System.out.println("Body: " + payload.toString());

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("=== RESPONSE ===");
        System.out.println("Status: " + response.statusCode());
        HttpHeaders respHdrs = response.headers();
        respHdrs.map().forEach((k,v)-> System.out.println(k + ": " + v));
        System.out.println("Body: " + response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Failed: " + response.statusCode() + " Body: " + response.body());
        }

        System.out.println("SUCCESS: Created content → " + response.body());
    }


    private static String safeText(JsonNode node, String path) {
        try {
            JsonNode n = node.at(path);
            if (n == null || n.isMissingNode() || n.isNull()) return null;
            return n.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
