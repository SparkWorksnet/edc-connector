package net.sparkworks.edc.extensions.ui;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
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
import java.util.stream.Collectors;

@Path("/")
public class CatalogUiController {

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
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("web/catalog.html")) {
            if (is == null) {
                return Response.status(404).entity("catalog.html not found").build();
            }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(html).build();
        } catch (Exception e) {
            monitor.severe("Failed to serve catalog page", e);
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

            var sb = new StringBuilder("[");
            boolean first = true;
            for (var asset : assets) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(asset.getId())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(asset.getName() != null ? asset.getName() : asset.getId())).append("\",");
                sb.append("\"contentType\":\"").append(escapeJson(getProperty(asset, "contenttype"))).append("\",");
                sb.append("\"description\":\"").append(escapeJson(getProperty(asset, "description"))).append("\",");

                var da = asset.getDataAddress();
                sb.append("\"dataAddress\":{");
                sb.append("\"type\":\"").append(escapeJson(da.getType())).append("\"");
                var bucket = da.getStringProperty("bucketName");
                if (bucket != null) {
                    sb.append(",\"bucketName\":\"").append(escapeJson(bucket)).append("\"");
                }
                var prefix = da.getStringProperty("prefix");
                if (prefix != null) {
                    sb.append(",\"prefix\":\"").append(escapeJson(prefix)).append("\"");
                }
                var endpoint = da.getStringProperty("endpoint");
                if (endpoint != null) {
                    sb.append(",\"endpoint\":\"").append(escapeJson(endpoint)).append("\"");
                }
                var baseUrl = da.getStringProperty("baseUrl");
                if (baseUrl != null) {
                    sb.append(",\"baseUrl\":\"").append(escapeJson(baseUrl)).append("\"");
                }
                sb.append("}");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list assets", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
        }
    }

    @GET
    @Path("catalog/api/contracts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listContracts() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var defs = contractDefinitionStore.findAll(query).collect(Collectors.toList());

            var sb = new StringBuilder("[");
            boolean first = true;
            for (var def : defs) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(def.getId())).append("\",");
                sb.append("\"accessPolicyId\":\"").append(escapeJson(def.getAccessPolicyId())).append("\",");
                sb.append("\"contractPolicyId\":\"").append(escapeJson(def.getContractPolicyId())).append("\"");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list contract definitions", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
        }
    }

    @GET
    @Path("catalog/api/policies")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPolicies() {
        try {
            var query = QuerySpec.Builder.newInstance().build();
            var policies = policyDefinitionStore.findAll(query).collect(Collectors.toList());

            var sb = new StringBuilder("[");
            boolean first = true;
            for (var policy : policies) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(policy.getId())).append("\"");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list policies", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
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
                return Response.serverError().entity("{\"error\":\"" + escapeJson(result.getFailureDetail()) + "\"}").build();
            }

            var agreements = result.getContent();
            var sb = new StringBuilder("[");
            boolean first = true;
            for (var agreement : agreements) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(agreement.getId())).append("\",");
                sb.append("\"assetId\":\"").append(escapeJson(agreement.getAssetId())).append("\",");
                sb.append("\"providerId\":\"").append(escapeJson(agreement.getProviderId())).append("\",");
                sb.append("\"consumerId\":\"").append(escapeJson(agreement.getConsumerId())).append("\",");
                sb.append("\"signingDate\":\"").append(formatEpoch(agreement.getContractSigningDate())).append("\"");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list agreements", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
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
                return Response.serverError().entity("{\"error\":\"" + escapeJson(result.getFailureDetail()) + "\"}").build();
            }

            var negotiations = result.getContent();
            var sb = new StringBuilder("[");
            boolean first = true;
            for (var neg : negotiations) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(neg.getId())).append("\",");
                sb.append("\"state\":\"").append(escapeJson(neg.stateAsString())).append("\",");
                sb.append("\"type\":\"").append(escapeJson(neg.getType().name())).append("\",");
                sb.append("\"counterPartyId\":\"").append(escapeJson(neg.getCounterPartyId())).append("\",");
                sb.append("\"counterPartyAddress\":\"").append(escapeJson(neg.getCounterPartyAddress())).append("\",");
                sb.append("\"protocol\":\"").append(escapeJson(neg.getProtocol())).append("\",");
                var agreement = neg.getContractAgreement();
                sb.append("\"agreementId\":\"").append(agreement != null ? escapeJson(agreement.getId()) : "").append("\"");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list negotiations", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
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
                return Response.serverError().entity("{\"error\":\"" + escapeJson(result.getFailureDetail()) + "\"}").build();
            }

            var transfers = result.getContent();
            var sb = new StringBuilder("[");
            boolean first = true;
            for (var tp : transfers) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{");
                sb.append("\"id\":\"").append(escapeJson(tp.getId())).append("\",");
                sb.append("\"state\":\"").append(escapeJson(tp.stateAsString())).append("\",");
                sb.append("\"type\":\"").append(escapeJson(tp.getType().name())).append("\",");
                sb.append("\"assetId\":\"").append(escapeJson(tp.getAssetId())).append("\",");
                sb.append("\"contractId\":\"").append(escapeJson(tp.getContractId())).append("\",");
                sb.append("\"transferType\":\"").append(escapeJson(tp.getTransferType())).append("\",");
                sb.append("\"counterPartyAddress\":\"").append(escapeJson(tp.getCounterPartyAddress())).append("\",");
                sb.append("\"destinationType\":\"").append(escapeJson(tp.getDestinationType())).append("\",");
                sb.append("\"createdAt\":\"").append(formatEpochMillis(tp.getCreatedAt())).append("\",");
                sb.append("\"stateTimestamp\":\"").append(formatEpochMillis(tp.getStateTimestamp())).append("\"");
                sb.append("}");
            }
            sb.append("]");

            return Response.ok(sb.toString()).build();
        } catch (Exception e) {
            monitor.severe("Failed to list transfers", e);
            return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
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

    private String formatEpoch(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochSecond(epochSeconds));
    }

    private String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0) return "";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochMilli(epochMillis));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}