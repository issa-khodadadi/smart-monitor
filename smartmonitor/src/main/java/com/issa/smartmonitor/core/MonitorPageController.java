package com.issa.smartmonitor.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
public class MonitorPageController {

    @GetMapping("/monitor")
    public ResponseEntity<String> page(HttpServletRequest request) throws Exception {
        ClassPathResource resource = new ClassPathResource("templates/monitor.html");
        String html;
        try (InputStream is = resource.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String contextPath = request.getContextPath();
        String injected = "<script>window.SMARTMONITOR_BASE_PATH = \"" + contextPath + "\";</script>";
        html = html.replace("</head>", injected + "\n</head>");

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}