package com.backendapp.demo.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.backendapp.demo.domain.Rol;
import com.backendapp.demo.domain.Usuario;
import com.backendapp.demo.dto.LoginResponse;
import com.backendapp.demo.dto.RegistroRequest;
import com.backendapp.demo.dto.UsuarioCreateRequest;
import com.backendapp.demo.dto.UsuarioDto;
import com.backendapp.demo.dto.UsuarioResponse;
import com.backendapp.demo.repository.RolRepository;
import com.backendapp.demo.repository.UsuarioRepository;

@Service
public class UsuarioService {

    /** Rol de quien se registra por su cuenta; los demas los asigna un administrador. */
    static final String ROL_REGISTRO = Rol.BENEFICIARIO;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CognitoService cognitoService;
    private final TransactionTemplate transactionTemplate;

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            CognitoService cognitoService, TransactionTemplate transactionTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.cognitoService = cognitoService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Login contra Cognito. Las llamadas HTTP a Cognito quedan fuera de la transaccion de base de datos.
     * El rol lo define el grupo de Cognito (fuente de verdad); el perfil local se crea en el primer login.
     */
    public LoginResponse login(String username, String password) {
        CognitoService.LoginResult result = cognitoService.login(username.trim(), password);
        String rolNombre = Rol.principal(result.groups())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "El usuario no tiene un rol asignado; contacte al administrador"));

        CognitoService.Perfil perfil = usuarioRepository.findByCognitoSub(result.cognitoSub()).isPresent()
                ? null
                : cognitoService.obtenerPerfil(result.username());

        UsuarioDto usuario = transactionTemplate.execute(status -> {
            Usuario u = usuarioRepository.findByCognitoSub(result.cognitoSub())
                    .or(() -> revincularPorUsername(result))
                    .orElseGet(() -> crearUsuarioDesdeLogin(result, perfil, rolNombre));
            if (Boolean.FALSE.equals(u.getActivo())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario desactivado");
            }
            if (!rolNombre.equals(u.getRol().getNombre())) {
                u.setRol(buscarRol(rolNombre));
            }
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNombre(), rolNombre);
        });
        return new LoginResponse(result.accessToken(), usuario);
    }

    /**
     * Usuario recreado en Cognito (mismo username, otro sub, ej. al volver a crear el User Pool):
     * se reutiliza el perfil local para conservar su id y su estado en vez de chocar con el username unico.
     */
    private Optional<Usuario> revincularPorUsername(CognitoService.LoginResult result) {
        return usuarioRepository.findByUsername(result.username())
                .map(u -> {
                    u.setCognitoSub(result.cognitoSub());
                    return u;
                });
    }

    private Usuario crearUsuarioDesdeLogin(CognitoService.LoginResult result, CognitoService.Perfil perfil,
            String rolNombre) {
        String email = perfil != null ? normalizarEmail(perfil.email()) : null;
        Usuario usuario = new Usuario();
        usuario.setCognitoSub(result.cognitoSub());
        usuario.setUsername(result.username());
        usuario.setNombre(perfil != null ? perfil.nombre() : result.username());
        usuario.setEmail(email != null && !usuarioRepository.existsByEmail(email) ? email : null);
        usuario.setRol(buscarRol(rolNombre));
        usuario.setActivo(Boolean.TRUE);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        String username = request.username().trim();
        String email = normalizarEmail(request.email());
        Rol rol = buscarRol(request.rol());

        if (usuarioRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese nombre de usuario");
        }
        if (email != null && usuarioRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese email");
        }

        String sub = cognitoService.createUser(username, email, request.nombre().trim(), rol.getNombre(),
                request.password());

        Usuario usuario = new Usuario();
        usuario.setCognitoSub(sub);
        usuario.setUsername(username);
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setRol(rol);
        usuario.setActivo(Boolean.TRUE);
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (RuntimeException e) {
            // No hay transaccion compartida entre Cognito y PostgreSQL: se compensa eliminando el usuario de Cognito
            cognitoService.deleteUser(username);
            if (e instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya existe");
            }
            throw e;
        }
        return toResponse(usuario);
    }

    /**
     * Auto-registro publico: crea la cuenta en Cognito y el perfil local con el rol basico. El rol nunca
     * viene del cliente. Igual que en el alta por administrador, si falla la base se elimina de Cognito.
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String email = normalizarEmail(request.email());
        String nombre = request.nombre().trim();
        Rol rol = buscarRol(ROL_REGISTRO);

        if (usuarioRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CognitoService.CUENTA_EXISTENTE);
        }

        String sub = cognitoService.registrar(email, nombre, rol.getNombre(), request.password());

        Usuario usuario = new Usuario();
        usuario.setCognitoSub(sub);
        // En un pool que usa el email como nombre de usuario, el username de Cognito es el sub
        usuario.setUsername(sub);
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setRol(rol);
        usuario.setActivo(Boolean.TRUE);
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (RuntimeException e) {
            cognitoService.deleteUser(email);
            if (e instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, CognitoService.CUENTA_EXISTENTE);
            }
            throw e;
        }
        return toResponse(usuario);
    }

    public void confirmarRegistro(String email, String codigo) {
        cognitoService.confirmarRegistro(normalizarEmail(email), codigo.trim());
    }

    public void reenviarCodigo(String email) {
        cognitoService.reenviarCodigo(normalizarEmail(email));
    }

    @Transactional
    public UsuarioResponse setActivo(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        usuario.setActivo(activo);
        usuarioRepository.save(usuario);

        // Si Cognito falla lanza 502 y la transaccion revierte el cambio local: ambos sistemas quedan como estaban
        cognitoService.setActivo(usuario.getUsername(), activo);

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

    /** Id local del usuario autenticado (para {@code registradoPor}); vacio si aun no ha iniciado sesion. */
    @Transactional(readOnly = true)
    public Optional<Long> idLocal(String cognitoSub) {
        return usuarioRepository.findByCognitoSub(cognitoSub).map(Usuario::getId);
    }

    private Rol buscarRol(String nombre) {
        if (!Rol.esValido(nombre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rol inválido: " + nombre + " (valores permitidos: " + String.join(", ", Rol.NOMBRES) + ")");
        }
        return rolRepository.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + nombre + " en la tabla rol"));
    }

    private static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getUsername(),
                u.getNombre(),
                u.getEmail(),
                u.getRol() != null ? u.getRol().getNombre() : null,
                u.getActivo());
    }
}
