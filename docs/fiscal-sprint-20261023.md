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
| FISC-08a | Unidade fiscal explícita e GTIN editáveis no Admin (`2474767`, `e08f382`, `913b4d9`, `1dfaed6`) | Nenhum valor é presumido; código errado pode ser apagado. |
| FISC-08b | Identidade fiscal congelada no item vendido (`8403037`) | Nome, GTIN e unidade não mudam quando o cadastro é editado; itens históricos incompletos são recusados. |
| FISC-08c | Conversão restrita de item simples (`8403037`) | Só item persistido, ativo, sem opções e com perfil explícito vira linha de NFC-e; CST 99 incompleto é recusado. |
| FISC-09a | Cerca persistente para tentativa de envio (`c4fb82a`, `f115f98`) | XML assinado precisa estar persistido; uma tentativa iniciada não é reenviada automaticamente. |
| FISC-08d1 | Desconto explícito no XML (`c8cfcbb`) | `vDesc` por item e total calculam `vNF` líquido; pagamentos precisam fechar esse total. |
| FISC-08d2 | Diagnóstico de GTIN/unidade (`e012f8a`) | Admin não considera item pronto sem identidade fiscal completa. |
| FISC-08e | Códigos fiscais congelados na venda (`42679ac`) | Mudanças posteriores no perfil não reclassificam uma comanda já criada. |
| FISC-09b1 | Reserva de número por emitente, ambiente e série (`7ffcbaa`) | Sequência não tem seed; reserva é atômica e só roda após provisionamento explícito. |
| FISC-08g | Revisão fiscal do cardápio no Admin (`504ecbc`) | Os 39 itens cruzados mostram evidência e confiança do vínculo sem aplicar códigos automaticamente. |
| FISC-09b2 | Cerca e reconciliação da transmissão (`dd1ce10`) | Envio em homologação exige tentativa durável e XSD; resposta incerta é consultada pela chave, sem reenvio. |
| FISC-09b3 | Validação explícita por XSD (`b437090`) | XML é recusado se o pacote oficial não tiver sido instalado; DTD e busca remota ficam bloqueados. |
| FISC-08f | Montagem da comanda fechada (`e60eb3b`) | Linhas congeladas, descontos e pagamentos compõem rascunho de homologação; opções sem regra fiscal e totais divergentes são recusados. |
| FISC-09c | Preparação assinada (`068a1e6`) | Reserva, assinatura, QR e XML persistido ocorrem na mesma transação; o serviço não transmite nem é registrado como bean. |
| FISC-09d1 | Identidade do A1 e rollback (`424aca2`) | CNPJ ICP-Brasil do certificado deve coincidir com emitente antes da reserva; teste de rollback/constraint em PostgreSQL. |
| FISC-09d2 | Documento pendente por comanda (`f522695`) | Comanda fechada gera um documento pendente; duas chamadas concorrentes reutilizam a mesma linha; reemissão exige decisão explícita. Sem endpoint nem envio. |

## Próximos tickets, em ordem de dependência

1. **FISC-08h — Opções vendidas.** Definir o tratamento fiscal dos adicionais
   e variantes com base no preço final e na classificação aprovada; até lá,
   itens com opções são recusados pela montagem fiscal.
2. **FISC-09d — Operação de homologação.** A ponte explícita entre documento
   pendente e preparação de homologação está em `1aa6224`; a unicidade por
   comanda está no banco pela Flyway V71 (`eed4353`). O XSD oficial está
   montado e conectado ao preparador explícito. Ainda falta concluir a
   numeração inicial conferida antes de ligar o fluxo. O A1 e sua senha já
   estão cifrados na VPS e passaram na validação Java transitória; ainda não
   estão montados na API. O rollback e a
   concorrência já foram testados no PostgreSQL.
3. **FISC-10 — Ensaio real de homologação.** Com classificação por item
   revisada, dados do emitente e A1 protegidos, obter autorização e ensaiar
   consulta, rejeição e cancelamento. Conferir XML, DANFE/QR Code e total.
4. **FISC-11 — Contingência e produção.** Implementar caminho legal de
   indisponibilidade, recuperação, numeração de produção, guarda de XML e
   rollback. Só então habilitar emissão no fechamento real.

Em 24/10, a VPS ainda tinha **0 perfis fiscais, 0 configuração do emitente,
0 produtos com perfil, 0 passos de opções e 0 documentos fiscais**; 39 itens
estavam visíveis no PDV interno. Esses números vêm de consultas de contagem,
sem dados pessoais ou segredos. O A1 permanece fora do Git; desde 27/10 há
duas credenciais cifradas na VPS (A1 e senha), sem acesso pela API. A conexão
mTLS só obteve WSDLs; nenhuma NFC-e foi transmitida.

Os endpoints e ambientes de São Paulo devem ser conferidos novamente no dia
do ensaio contra a [página oficial de WebServices da SEFAZ-SP](https://portal.fazenda.sp.gov.br/servicos/nfce/Paginas/WebServices.aspx).

Em 24/10, o ZIP oficial `PL_010f_v1.04.zip` foi baixado do
[Portal Nacional da NF-e](https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w%3D),
teve seu SHA-256 conferido com a fixture existente e foi extraído em
`/opt/farelo-os/shared/xsd/PL_010f_v1.04/` na VPS. A API tem montagem somente
leitura em `/opt/farelo-os/xsd`, e a preparação explícita pode ser construída
por `HomologationNfcePreparationService.usingMountedOfficialSchemas(...)`.
Essa fábrica fixa a versão 010f v1.04 e confere SHA-256 dos cinco XSD necessários;
pacote ausente ou alterado impede a construção do serviço. Isso não registra
bean, endpoint ou transmissão, nem libera emissão. Confirmar a versão oficial
vigente no dia do ensaio antes de usar o fluxo.
