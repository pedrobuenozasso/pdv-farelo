# Fiscal: decisões que dependem do Farelo e da contabilidade

Estado reconfirmado em 29/10/2026: Web `1031ad3` e API `2da26ab` estão na VPS privada. O caixa **não** emite NFC-e; nenhum XML foi enviado à SEFAZ. O banco tem 52 produtos, 1 configuração do emitente parcialmente preenchida, 0 perfis fiscais, 0 sequências e 0 documentos. O A1 e a senha estão fora do Git, cifrados na VPS como credenciais do systemd e sem montagem na API; ver `deploy/fiscal/README.md`. Existe uma rota administrativa idempotente para criar o documento **pendente** de comanda fechada e paga; ela não reserva número, assina nem transmite XML.

## O que Pedro e a contabilidade precisam conferir

1. **Emitente atual.** Na aba de configuração fiscal do Admin, conferir CNPJ, inscrição estadual, CRT, razão social e endereço de emissão. Em 29/10, CRT 1 foi salvo, mas ainda precisa ser cruzado com o XML atual ou com a contabilidade; inscrição estadual e código IBGE estão vazios. Salvar novamente pelo formulário normaliza o CNPJ hoje pontuado. Os XMLs antigos do Sischef são evidência histórica, mas não substituem a confirmação de que esses dados continuam válidos.
2. **Classificação do cardápio.** Abrir `/admin/fiscal-review` e confirmar os vínculos dos 39 itens ativos com o Sischef. A tela separa 12 vínculos de alta confiança, 18 médios, 4 ambíguos e 5 sem equivalente seguro. Para cada produto, validar com a contabilidade NCM, eventual CEST, CFOP, CSOSN/CST, origem ICMS, PIS/COFINS, unidade fiscal e GTIN real ou `SEM GTIN`. Os 13 itens adicionais pausados precisam de revisão antes de ativação. O relatório detalhado está em `apps/web/src/data/fiscal-review-decisions-20261023.md`.
3. **Produtos com opções.** Confirmar como adicionais e variantes aparecem na venda e no documento fiscal, com preço final e classificação de cada componente. O sistema bloqueia a montagem fiscal desses itens até haver regra explícita.
4. **Numeração e credencial.** Confirmar a série e o próximo número de NFC-e **separadamente para homologação e produção** com a contabilidade/Sischef, mediante evidência atual; a DANFE de 24/10 mostra um número histórico e não define o próximo. O A1 foi localizado no Mac em duas cópias idênticas, ambas com permissão local `600`; cópias cifradas do A1 e da senha foram instaladas na VPS, separadas da API. Nunca inserir a senha por chat, Git ou formulário improvisado.

## Trabalho técnico que permanece com a equipe

- Reconfirmar a vigência do pacote XSD oficial 010f no dia do ensaio. O pacote já está montado somente para leitura na API e o fluxo explícito consegue verificar sua integridade; não buscar esquemas pela internet em tempo de emissão.
- A custódia cifrada do A1 e da senha na VPS foi instalada sem expor os segredos à API; faltam rotação/recuperação testadas e ligação ao serviço fiscal isolado. A tentativa anterior de transferir o A1 como arquivo simples foi rejeitada pela revisão automática e não foi executada. A ponte explícita entre documento pendente e preparação de homologação já existe, sem endpoint ou transmissão; provisionar numeração apenas com valores conferidos. A checagem do CNPJ do certificado, o rollback da preparação e a concorrência na criação do documento já passaram nos testes.
- Executar homologação real, consulta de resposta incerta, rejeição e cancelamento; conferir XML, DANFE e QR Code.
- Implementar contingência, guarda/backup externo de XML e procedimento de recuperação antes de habilitar emissão em produção.

As classificações sugeridas pelo Sischef não foram aplicadas automaticamente. A revisão pode ser feita aos poucos; a emissão continuará bloqueada enquanto os campos necessários faltarem.
