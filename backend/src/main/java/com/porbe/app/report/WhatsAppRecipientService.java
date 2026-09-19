package com.porbe.app.report;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administra los números autorizados para entregas manuales y automáticas. */
@Service
public class WhatsAppRecipientService {

    private final WhatsAppRecipientRepository repository;

    public WhatsAppRecipientService(WhatsAppRecipientRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WhatsAppRecipientResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<WhatsAppRecipient> active() {
        return repository.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public boolean hasActive() {
        return repository.existsByEnabledTrue();
    }

    @Transactional
    public WhatsAppRecipientResponse create(WhatsAppRecipientRequest request, String username) {
        var number = validNumber(request.phoneNumber());
        ensureUnique(number, null);
        return response(repository.save(new WhatsAppRecipient(
                request.name().trim(), number, request.enabled(), username)));
    }

    @Transactional
    public WhatsAppRecipientResponse update(Long id, WhatsAppRecipientRequest request, String username) {
        var recipient = recipient(id);
        var number = validNumber(request.phoneNumber());
        ensureUnique(number, id);
        recipient.update(request.name().trim(), number, request.enabled(), username);
        return response(repository.save(recipient));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(recipient(id));
    }

    @Transactional(readOnly = true)
    public WhatsAppRecipient active(Long id) {
        var recipient = recipient(id);
        if (!recipient.isEnabled()) {
            throw new IllegalArgumentException("Activa el destinatario antes de enviar el informe.");
        }
        return recipient;
    }

    private WhatsAppRecipient recipient(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El destinatario de WhatsApp no existe."));
    }

    private String validNumber(String value) {
        var number = value.replaceAll("\\D", "");
        if (!number.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("El número debe incluir el código de país y tener entre 8 y 15 dígitos.");
        }
        return number;
    }

    private void ensureUnique(String number, Long currentId) {
        repository.findByPhoneNumber(number)
                .filter(recipient -> !recipient.getId().equals(currentId))
                .ifPresent(recipient -> {
                    throw new IllegalArgumentException("Ese número de WhatsApp ya está configurado.");
                });
    }

    private WhatsAppRecipientResponse response(WhatsAppRecipient recipient) {
        return new WhatsAppRecipientResponse(
                recipient.getId(),
                recipient.getName(),
                recipient.getPhoneNumber(),
                recipient.isEnabled(),
                recipient.getUpdatedBy(),
                recipient.getUpdatedAt());
    }
}
