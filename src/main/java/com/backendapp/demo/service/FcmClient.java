package com.backendapp.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

@Component
public class FcmClient {

    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);

    public boolean sendNotification(String fcmToken, String title, String body) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FCM no configurado. Notificación no enviada: {} - {}", title, body);
            return false;
        }

        Notification notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        Message message = Message.builder()
            .setToken(fcmToken)
            .setNotification(notification)
            .build();

        try {
            FirebaseMessaging.getInstance().send(message);
            log.info("Notificación enviada a token {}: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())), title);
            return true;
        } catch (FirebaseMessagingException e) {
            if (e.getErrorCode().name().contains("NOT_FOUND") || e.getErrorCode().name().contains("INVALID_ARGUMENT")) {
                log.warn("Token FCM inválido: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())));
            } else {
                log.error("Error enviando notificación a {}: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())), e.getMessage());
            }
            return false;
        }
    }

    public boolean sendNotificationWithData(String fcmToken, String title, String body, String tipo, String eventoId) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FCM no configurado. Notificación no enviada: {} - {}", title, body);
            return false;
        }

        Notification notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        Message message = Message.builder()
            .setToken(fcmToken)
            .setNotification(notification)
            .putData("tipo", tipo)
            .putData("evento_id", eventoId)
            .build();

        try {
            FirebaseMessaging.getInstance().send(message);
            log.info("Notificación con datos enviada a token {}: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())), title);
            return true;
        } catch (FirebaseMessagingException e) {
            if (e.getErrorCode().name().contains("NOT_FOUND") || e.getErrorCode().name().contains("INVALID_ARGUMENT")) {
                log.warn("Token FCM inválido: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())));
            } else {
                log.error("Error enviando notificación a {}: {}", fcmToken.substring(0, Math.min(20, fcmToken.length())), e.getMessage());
            }
            return false;
        }
    }
}
