# Fiscal: decisões que dependem do Farelo e da contabilidade

Estado conferido em 26/10/2026: o release `d058180` está na VPS privada. O caixa **não** emite NFC-e; nenhum XML foi enviado à SEFAZ. O banco tem 52 produtos e ainda não tem perfil fiscal, configuração do emitente, sequência fiscal ou documento emitido. O A1 e sua senha continuam fora do Git e da VPS. Existe uma rota administrativa idempotente para criar o documento **pendente** de comanda fechada e paga; ela não reserva número, assina nem transmite XML.

## O que Pedro e a contabilidade precisam conferir

1. **Emitente atual.** Na aba de configuração fiscal do Admin, conferir CNPJ, inscrição estadual, CRT, razão social e endereço de emissão. Os XMLs antigos do Sischef são evidência histórica, mas não substituem a confirmação de que esses dados continuam válidos.
2. **Classificação do cardápio.** Abrir `/admin/fiscal-review` e confirmar os vínculos dos 39 itens ativos com o Sischef. A tela separa 12 vínculos de alta confiança, 18 médios, 4 ambíguos e 5 sem equivalente seguro. Para cada produto, validar com a contabilidade NCM, eventual CEST, CFOP, CSOSN/CST, origem ICMS, PIS/COFINS, unidade fiscal e GTIN real ou `SEM GTIN`. Os 13 itens adicionais pausados precisam de revisão antes de ativação. O relatório detalhado está em `apps/web/src/data/fiscal-review-decisions-20261023.md`.
3. **Produtos com opções.** Confirmar como adicionais e variantes aparecem na venda e no documento fiscal, com preço final e classificação de cada componente. O sistema bloqueia a montagem fiscal desses itens até haver regra explícita.
4. **Numeração e credencial.** Confirmar a série e o próximo número de NFC-e **separadamente para homologação e produção** com a contabilidade/Sischef, mediante evidência atual; a DANFE de 24/10 mostra um número histórico e não define o próximo. O A1 foi localizado no Mac em duas cópias idênticas; ambas têm permissão local `600`. A senha deve ser inserida somente no Terminal local ou no mecanismo seguro que vier a ser implantado; nunca por chat, Git ou formulário improvisado. Nenhuma cópia foi enviada à VPS.

## Trabalho técnico que permanece com a equipe

- Reconfirmar a vigência do pacote XSD oficial 010f no dia do ensaio. O pacote já está montado somente para leitura na API e o fluxo explícito consegue verificar sua integridade; não buscar esquemas pela internet em tempo de emissão.
- Definir e implantar custódia do A1 **com criptografia adicional em repouso**, acesso mínimo, rotação e recuperação testada antes de copiá-lo à VPS. Uma tentativa de transferência por SSH para arquivo `root:root` modo `600` foi rejeitada pela revisão automática por persistir o certificado sem essa camada de proteção; **não foi executada**. Não contornar a rejeição com outro comando de cópia. Integrar a rota de documento pendente ao fluxo explícito e provisionar a numeração apenas com valores conferidos. A checagem do CNPJ do certificado, o rollback da preparação e a concorrência na criação do documento já passaram nos testes.
- Executar homologação real, consulta de resposta incerta, rejeição e cancelamento; conferir XML, DANFE e QR Code.
- Implementar contingência, guarda/backup externo de XML e procedimento de recuperação antes de habilitar emissão em produção.

As classificações sugeridas pelo Sischef não foram aplicadas automaticamente. A revisão pode ser feita aos poucos; a emissão continuará bloqueada enquanto os campos necessários faltarem.
