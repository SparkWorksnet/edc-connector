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
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Path("/")
public class CatalogUiController {

    private final AssetIndex assetIndex;
    private final ContractDefinitionStore contractDefinitionStore;
    private final PolicyDefinitionStore policyDefinitionStore;
    private final Monitor monitor;

    public CatalogUiController(AssetIndex assetIndex,
                               ContractDefinitionStore contractDefinitionStore,
                               PolicyDefinitionStore policyDefinitionStore,
                               Monitor monitor) {
        this.assetIndex = assetIndex;
        this.contractDefinitionStore = contractDefinitionStore;
        this.policyDefinitionStore = policyDefinitionStore;
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
    @Path("catalog/api/assets/{assetId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadAsset(@PathParam("assetId") String assetId) {
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}