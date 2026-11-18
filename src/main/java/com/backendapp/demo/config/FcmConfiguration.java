package com.backendapp.demo.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class FcmConfiguration {

    public FcmConfiguration(FcmProperties properties) throws IOException {
        if (properties.credentialsPath() == null || properties.credentialsPath().isBlank()) {
            // FCM no configurado, se puede usar en modo desarrollo sin notificaciones
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            try (var is = new FileInputStream(properties.credentialsPath())) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .setProjectId(properties.projectId())
                    .build();
                FirebaseApp.initializeApp(options);
            }
        }
    }
}
