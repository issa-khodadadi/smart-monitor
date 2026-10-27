package com.issa.smartmonitor.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.issa.smartmonitor.config.SmartMonitorProperties;

import java.io.InputStream;

/**
 * View schemas shipped beside the defaults. Adding a column there is enough for the dashboard to show it.
 */
public class ViewCatalog {

    private final JsonNode root;

    public ViewCatalog(SmartMonitorProperties properties, ObjectMapper mapper) {
        String resource = properties.getViews().getResource();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("View schema resource not found: " + resource);
            }
            this.root = mapper.readTree(in);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read view schema " + resource, ex);
        }
    }

    public JsonNode schema(String view) {
        JsonNode node = root.get(view);
        return node == null ? null : node.deepCopy();
    }
}
