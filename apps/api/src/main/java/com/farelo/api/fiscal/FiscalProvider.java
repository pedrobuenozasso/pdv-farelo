package com.farelo.api.fiscal;

/**
 * FARELO-170: boundary between Farelo's fiscal workflow and an NFC-e authorizer.
 * Implementations may use a fiscal provider or a SEFAZ adapter; neither the
 * domain nor the calling service needs to know which one is configured.
 *
 * <p>This contract only submits an already signed XML. Building and signing
 * that XML, choosing homologation or production, interpreting the response,
 * consulting an uncertain submission, and persisting its outcome belong to
 * later Epic 12 tickets. In particular, a network timeout must never be
 * interpreted as rejection or used to blindly submit the same document again.
 * No implementation is registered by this ticket, so this interface cannot
 * emit a document on its own.
 *
 * @see <a href="https://portal.fazenda.sp.gov.br/servicos/nfce/">SEFAZ-SP NFC-e</a>
 */
public interface FiscalProvider {

    /**
     * Submits one signed NFC-e XML and returns the unmodified authorizer reply.
     * The later workflow must verify the reply before changing document state.
     * A failed call may have reached the authorizer; callers must consult the
     * document's access key before deciding whether another send is safe.
     */
    FiscalAuthorizationResponse authorize(FiscalAuthorizationRequest request);
}
