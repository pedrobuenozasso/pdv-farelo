# Fiscal — tickets pequenos para a primeira NFC-e do Farelo OS

Meta: uma venda de teste em Comanda/mesa gera NFC-e autorizada em homologação,
recuperável por chave, com os itens, descontos e pagamentos da comanda. Nenhum
ticket isolado autoriza venda fiscal em produção.

## Entregas de 23/10

| Ticket | Entrega | Aceite |
| --- | --- | --- |
| FISC-01 | GTIN explícito no produto e no XML (`6ade41a`) | Código ausente/inválido é recusado; nenhum código é inventado. |
| FISC-02 | Emitente estruturado, V63 e mapper (`936a7bf`) | CRT/endereço são fornecidos explicitamente; texto livre não é convertido em dado fiscal. |
| FISC-03 | Diagnóstico de pendências no Admin (`ce37244`, `01f16c8`) | Produtos e emitente incompletos aparecem por nome/campo, sem selo enganoso de liberação. |
| FISC-04 | Preparação segura do evento de cancelamento (`fe423c6`) | Só nota autorizada elegível gera evento assinado; não existe envio sem tentativa durável. |
| FISC-05 | Campos fiscais explícitos no perfil (`eef1ba5`) | Origem, CEST e CST PIS/COFINS são opcionais, validados e nunca presumidos. |
| FISC-06 | Editor do emitente (`e6d671e`) | ADM pode revisar e salvar todos os campos V63. |
| FISC-07 | Chave de acesso única (`6eb2bab`) | Banco recusa duplicidade de chave não nula; rascunhos sem chave coexistem. |

## Próximos tickets, em ordem de dependência

1. **FISC-08 — Montagem da venda.** Criar rascunho fiscal dos snapshots de
   itens, opções, descontos e pagamentos da comanda. Rejeitar valores que o
   XML suportado não representa, sem recalcular preços pelo catálogo atual.
2. **FISC-09 — Tentativa durável e reconciliação.** Reservar número/chave,
   persistir XML assinado antes do envio, transmitir uma vez, consultar a chave
   após resposta incerta e registrar autorização ou rejeição confirmada.
3. **FISC-10 — Ensaio real de homologação.** Com classificação por item
   revisada, dados do emitente e A1 protegidos, obter autorização e ensaiar
   consulta, rejeição e cancelamento. Conferir XML, DANFE/QR Code e total.
4. **FISC-11 — Contingência e produção.** Implementar caminho legal de
   indisponibilidade, recuperação, numeração de produção, guarda de XML e
   rollback. Só então habilitar emissão no fechamento real.

Em 23/10, a VPS ainda tinha **0 perfis fiscais, 0 configuração do emitente,
0 produtos com perfil, 0 passos de opções e 0 documentos fiscais**; 39 itens
estavam visíveis no PDV interno. Esses números vêm de consultas de contagem,
sem dados pessoais ou segredos. O A1 permanece fora do Git e da VPS. A conexão
mTLS só obteve WSDLs; nenhuma NFC-e foi transmitida.

Os endpoints e ambientes de São Paulo devem ser conferidos novamente no dia
do ensaio contra a [página oficial de WebServices da SEFAZ-SP](https://portal.fazenda.sp.gov.br/servicos/nfce/Paginas/WebServices.aspx).
