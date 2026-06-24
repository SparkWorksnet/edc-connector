package net.sparkworks.edc.extensions.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/")
public class CatalogUiController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> SKIP_PROPS = Set.of("name", "contenttype", "description", "id");
    private static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    private final AssetIndex assetIndex;
    private final ContractDefinitionStore contractDefinitionStore;
    private final PolicyDefinitionStore policyDefinitionStore;
    private final ContractAgreementService contractAgreementService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final Monitor monitor;

    public CatalogUiController(AssetIndex assetIndex,
                               ContractDefinitionStore contractDefinitionStore,
                               PolicyDefinitionStore policyDefinitionStore,
                               ContractAgreementService contractAgreementService,
                               ContractNegotiationService contractNegotiationService,
                               TransferProcessService transferProcessService,
                               Monitor monitor) {
        this.assetIndex = assetIndex;
        this.contractDefinitionStore = contractDefinitionStore;
        this.policyDefinitionStore = policyDefinitionStore;
        this.contractAgreementService = contractAgreementService;
        this.contractNegotiationService = contractNegotiationService;
        this.transferProcessService = transferProcessService;
        this.monitor = monitor;
    }

    @GET
    @Path("catalog")
    @Produces(MediaType.TEXT_HTML)
    public Response catalogPage() {
        return servePage("web/catalog.html");
    }

    @GET
    @Path("catalog/submit")
    @Produces(MediaType.TEXT_HTML)
    public Response submitPage() {
        return servePage("web/submit-dataset.html");
    }

    private Response servePage(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                return Response.status(404).entity(resource + " not found").build();
            }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(html).build();
        } catch (Exception e) {
            monitor.severe("Failed to serve " + resource, e);
            return Response.serverError().entity("Error loading page").build();
        }
    }

    @GET
    @Path("catalog/api/assets")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAssets() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var assets = assetIndex.queryAssets(query).collect(Collectors.toList());

            ArrayNode arr = MAPPER.createArrayNode();
            for (var asset : assets) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", asset.getId());
                node.put("name", asset.getName() != null ? asset.getName() : asset.getId());
                node.put("contentType", getProperty(asset, "contenttype"));
                node.put("description", getProperty(asset, "description"));

                var da = asset.getDataAddress();
                ObjectNode daNode = MAPPER.createObjectNode();
                daNode.put("type", da.getType());
                putIfPresent(daNode, "bucketName", da.getStringProperty("bucketName"));
                putIfPresent(daNode, "prefix", da.getStringProperty("prefix"));
                putIfPresent(daNode, "endpoint", da.getStringProperty("endpoint"));
                putIfPresent(daNode, "baseUrl", da.getStringProperty("baseUrl"));
                node.set("dataAddress", daNode);

                ObjectNode meta = MAPPER.createObjectNode();
                for (var entry : asset.getProperties().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith(EDC_NS)) {
                        key = key.substring(EDC_NS.length());
                    }
                    if (SKIP_PROPS.contains(key)) continue;
                    meta.put(key, String.valueOf(entry.getValue()));
                }
                node.set("metadata", meta);

                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list assets", e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/contracts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listContracts() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var defs = contractDefinitionStore.findAll(query).collect(Collectors.toList());

            ArrayNode arr = MAPPER.createArrayNode();
            for (var def : defs) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", def.getId());
                node.put("accessPolicyId", def.getAccessPolicyId());
                node.put("contractPolicyId", def.getContractPolicyId());
                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list contract definitions", e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/policies")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPolicies() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var policies = policyDefinitionStore.findAll(query).collect(Collectors.toList());

            ArrayNode arr = MAPPER.createArrayNode();
            for (var policy : policies) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", policy.getId());
                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list policies", e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/agreements")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAgreements() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var result = contractAgreementService.search(query);
            if (result.failed()) {
                return Response.serverError().entity("{\"error\":\"" + result.getFailureDetail() + "\"}").build();
            }

            ArrayNode arr = MAPPER.createArrayNode();
            for (var agreement : result.getContent()) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", agreement.getId());
                node.put("assetId", agreement.getAssetId());
                node.put("providerId", agreement.getProviderId());
                node.put("consumerId", agreement.getConsumerId());
                node.put("signingDate", formatEpoch(agreement.getContractSigningDate()));
                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list agreements", e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/negotiations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listNegotiations() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var result = contractNegotiationService.search(query);
            if (result.failed()) {
                return Response.serverError().entity("{\"error\":\"" + result.getFailureDetail() + "\"}").build();
            }

            ArrayNode arr = MAPPER.createArrayNode();
            for (var neg : result.getContent()) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", neg.getId());
                node.put("state", neg.stateAsString());
                node.put("type", neg.getType().name());
                node.put("counterPartyId", neg.getCounterPartyId());
                node.put("counterPartyAddress", neg.getCounterPartyAddress());
                node.put("protocol", neg.getProtocol());
                var agreement = neg.getContractAgreement();
                node.put("agreementId", agreement != null ? agreement.getId() : "");
                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list negotiations", e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/transfers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTransfers() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var result = transferProcessService.search(query);
            if (result.failed()) {
                return Response.serverError().entity("{\"error\":\"" + result.getFailureDetail() + "\"}").build();
            }

            ArrayNode arr = MAPPER.createArrayNode();
            for (var tp : result.getContent()) {
                ObjectNode node = MAPPER.createObjectNode();
                node.put("id", tp.getId());
                node.put("state", tp.stateAsString());
                node.put("type", tp.getType().name());
                node.put("assetId", tp.getAssetId());
                node.put("contractId", tp.getContractId());
                node.put("transferType", tp.getTransferType());
                node.put("counterPartyAddress", tp.getCounterPartyAddress());
                node.put("destinationType", tp.getDestinationType());
                node.put("stateTimestamp", formatEpochMillis(tp.getStateTimestamp()));
                arr.add(node);
            }

            return Response.ok(MAPPER.writeValueAsString(arr)).build();
        } catch (Exception e) {
            monitor.severe("Failed to list transfers", e);
            return jsonError(e);
        }
    }

    @POST
    @Path("catalog/api/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(@jakarta.ws.rs.QueryParam("bucket") String bucket,
                               @jakarta.ws.rs.QueryParam("key") String key,
                               @jakarta.ws.rs.QueryParam("endpoint") String endpoint,
                               @jakarta.ws.rs.QueryParam("accessKey") String accessKey,
                               @jakarta.ws.rs.QueryParam("secretKey") String secretKey,
                               InputStream body) {
        try {
            if (bucket == null || key == null) {
                return Response.status(400).entity("{\"error\":\"bucket and key are required\"}").build();
            }
            var ep = endpoint != null ? endpoint : "http://rustfs:9000";
            var ak = accessKey != null ? accessKey : "participant-admin";
            var sk = secretKey != null ? secretKey : "participant-secret-2024";

            var client = MinioClient.builder().endpoint(ep).credentials(ak, sk).build();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(body, -1, 10485760)
                    .contentType("application/octet-stream")
                    .build());

            ObjectNode result = MAPPER.createObjectNode();
            result.put("bucket", bucket);
            result.put("key", key);
            result.put("status", "uploaded");
            return Response.ok(MAPPER.writeValueAsString(result)).build();
        } catch (Exception e) {
            monitor.severe("Failed to upload file: " + key, e);
            return jsonError(e);
        }
    }

    @GET
    @Path("catalog/api/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadAsset(@jakarta.ws.rs.QueryParam("assetId") String assetId) {
        try {
            var asset = assetIndex.findById(assetId);
            if (asset == null) {
                return Response.status(404).entity("Asset not found: " + assetId).build();
            }

            var da = asset.getDataAddress();
            var type = da.getType();

            if ("MinioAsset".equals(type) || "MinioFiles".equals(type)) {
                var endpoint = da.getStringProperty("endpoint");
                var bucket = da.getStringProperty("bucketName");
                var accessKey = da.getStringProperty("accessKey");
                var secretKey = da.getStringProperty("secretKey");
                var prefix = da.getStringProperty("prefix");

                var client = MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKey, secretKey)
                        .build();

                var stream = client.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(prefix)
                        .build());

                var fileName = prefix.contains("/") ? prefix.substring(prefix.lastIndexOf('/') + 1) : prefix;

                return Response.ok(stream)
                        .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                        .build();

            } else if ("HttpData".equals(type)) {
                var baseUrl = da.getStringProperty("baseUrl");
                return Response.temporaryRedirect(java.net.URI.create(baseUrl)).build();

            } else {
                return Response.status(400).entity("Download not supported for type: " + type).build();
            }

        } catch (Exception e) {
            monitor.severe("Failed to download asset: " + assetId, e);
            return Response.serverError().entity("Download failed: " + e.getMessage()).build();
        }
    }

    private String getProperty(org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset asset, String key) {
        var val = asset.getProperty(key);
        return val != null ? val.toString() : "";
    }

    private void putIfPresent(ObjectNode node, String key, String value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private String formatEpoch(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        return UTC_FMT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0) return "";
        return UTC_FMT.format(Instant.ofEpochMilli(epochMillis));
    }

    private Response jsonError(Exception e) {
        try {
            ObjectNode err = MAPPER.createObjectNode();
            err.put("error", e.getMessage());
            return Response.serverError().entity(MAPPER.writeValueAsString(err)).build();
        } catch (Exception ex) {
            return Response.serverError().entity("{\"error\":\"internal error\"}").build();
        }
    }
}
