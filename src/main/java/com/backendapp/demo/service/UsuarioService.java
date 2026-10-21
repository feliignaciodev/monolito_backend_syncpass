package com.backendapp.demo.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.domain.Rol;
import com.backendapp.demo.domain.Usuario;
import com.backendapp.demo.dto.LoginResponse;
import com.backendapp.demo.dto.UsuarioCreateRequest;
import com.backendapp.demo.dto.UsuarioResponse;
import com.backendapp.demo.repository.RolRepository;
import com.backendapp.demo.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CognitoService cognitoService;

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            CognitoService cognitoService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.cognitoService = cognitoService;
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        CognitoService.LoginResult result = cognitoService.login(email, password);

        String rolNombre = result.groups().isEmpty() ? null : result.groups().get(0);

        Usuario usuario = usuarioRepository.findByCognitoSub(result.cognitoSub())
                .orElseGet(() -> crearUsuarioDesdeLogin(result.cognitoSub(), email, rolNombre));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario desactivado");
        }

        String rolFinal = (usuario.getRol() != null) ? usuario.getRol().getNombre()
                : (rolNombre != null ? rolNombre : "");

        return new LoginResponse(
                result.accessToken(),
                rolFinal,
                result.expiresIn(),
                new LoginResponse.UsuarioResumen(usuario.getId(), usuario.getNombre()));
    }

    private Usuario crearUsuarioDesdeLogin(String sub, String email, String rolNombre) {
        Rol rol = obtenerOCrearRol(rolNombre != null ? rolNombre : Rol.BENEFICIARIO);
        Usuario usuario = new Usuario();
        usuario.setCognitoSub(sub);
        usuario.setEmail(email);
        usuario.setNombre(email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
        usuario.setRol(rol);
        usuario.setActivo(Boolean.TRUE);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        Rol rol = obtenerOCrearRol(request.rol());

        String sub = cognitoService.createUser(request.email(), request.nombre(), request.rol(), request.password());

        // Evitar duplicados por sub
        if (usuarioRepository.findByCognitoSub(sub).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya existe");
        }

        Usuario usuario = new Usuario();
        usuario.setCognitoSub(sub);
        usuario.setNombre(request.nombre());
        usuario.setEmail(request.email());
        usuario.setRol(rol);
        usuario.setActivo(Boolean.TRUE);
        usuario = usuarioRepository.save(usuario);

        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse setActivo(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        usuario.setActivo(activo);
        usuarioRepository.save(usuario);

        try {
            cognitoService.setActivo(usuario.getEmail(), activo);
        } catch (Exception e) {
            // Se mantiene el cambio en BD; el error de Cognito se propaga como 502
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error actualizando estado en Cognito", e);
        }

        return toResponse(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return toResponse(usuario);
    }

    private Rol obtenerOCrearRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombre(),
                u.getEmail(),
                u.getRol() != null ? u.getRol().getNombre() : null,
                u.getActivo());
    }
}