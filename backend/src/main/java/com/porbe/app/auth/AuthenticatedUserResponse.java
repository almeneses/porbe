package com.porbe.app.auth;

import java.util.List;

/** Vista pública del usuario asociado a la sesión actual. */
public record AuthenticatedUserResponse(String username, List<String> roles) {
}
