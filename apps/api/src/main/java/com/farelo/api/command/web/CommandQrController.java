package com.farelo.api.command.web;

import com.farelo.api.command.CommandService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Current-visit QR secrets for staff printing; never returned by public command reads. */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandQrController {

    private final CommandService commandService;

    public CommandQrController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping("/qr")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<List<CommandQrResponse>> listCurrentQrTokens() {
        List<CommandQrResponse> codes = commandService.listAll().stream()
                .map(command -> new CommandQrResponse(command.getNumber(), command.getQrToken()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(codes);
    }

    public record CommandQrResponse(int number, UUID qrToken) {
    }
}
