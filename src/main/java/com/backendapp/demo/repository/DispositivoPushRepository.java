package com.backendapp.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.DispositivoPush;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByfcmToken(String fcmToken);

    List<DispositivoPush> findByUsuario_IdAndActivoTrue(Long usuarioId);

    List<DispositivoPush> findByActivoTrue();
}
