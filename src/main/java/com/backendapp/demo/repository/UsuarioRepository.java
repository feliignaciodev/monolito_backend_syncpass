package com.backendapp.demo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backendapp.demo.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCognitoSub(String cognitoSub);

    Optional<Usuario> findByEmail(String email);
}