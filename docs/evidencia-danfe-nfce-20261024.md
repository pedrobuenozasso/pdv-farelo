# Evidência sanitizada de DANFE NFC-e do sistema atual

Em 24/10/2026, o proprietário mostrou uma foto de um DANFE NFC-e emitido pela cafeteria no sistema atual. A foto **não** foi copiada para este repositório: contém identificação do consumidor, chave de acesso e QR Code. Este resumo mantém apenas dados de produto e operação úteis ao ensaio do Farelo OS. Ele não é XML fiscal nem comprova emissão pelo Farelo OS.

## Venda observada

| Descrição impressa | Quantidade | Unidade impressa | Valor (R$) |
| --- | ---: | --- | ---: |
| Bolo de Red Velvet com Cream Cheese | 1 | FAT | 23,00 |
| Café expresso longo | 1 | UN | 10,00 |
| Cestinha de mini pães de queijo | 1 | UN | 15,00 |
| Pão com ovo e bacon | 1 | UN | 28,00 |
| Suco laranja integral | 1 | UN | 12,00 |

Cinco itens, total de produtos **R$ 88,00**, desconto **R$ 0,00**, outras despesas **R$ 0,00** e total pago **R$ 88,00** por **cartão de crédito** em maquininha separada. O papel mostra série **1**. O número da nota foi omitido aqui por privacidade; esta foto tampouco permite determinar o **próximo** número disponível.

## Cruzamento com o Farelo OS

Consulta somente leitura ao catálogo da VPS em 24/10/2026. `POS` e `menu` são indicadores distintos; esta tabela não altera visibilidade ou preço.

| Item do papel | Cadastro encontrado | Preço | POS | Menu | Pendente |
| --- | --- | ---: | --- | --- | --- |
| Red Velvet com Cream Cheese | Não encontrado por nome/termos na consulta | — | — | — | Identificar se falta cadastro, se é variante ou se usa outro nome. |
| Café expresso longo | Café expresso longo(carioca) | R$ 10,00 | Sim | Não | Confirmar equivalência da variante e visibilidade. |
| Cestinha de mini pães de queijo | Cestinha de mini pães de queijo | R$ 15,00 | Sim | Não | Conferir unidade e visibilidade. |
| Pão com ovo e bacon | Pão na Chapa com Ovos Mexidos e Bacon | R$ 28,00 | Sim | Sim | Confirmar equivalência da receita/variante. |
| Suco laranja integral | Suco de Laranja INTEGRAL | R$ 12,00 | Sim | Não | Conferir visibilidade. |

Nenhum dos quatro produtos localizados tinha perfil fiscal vinculado na VPS. O DANFE fotografado **não exibe** NCM, CFOP, CEST, CSOSN/CST, GTIN ou regras para adicionais. Os números na coluna `COD` do papel são códigos de item do sistema atual; não devem ser usados como NCM, GTIN nem ID do Farelo OS. O cruzamento de nomes e preços sugere candidatos, mas não aprova a classificação fiscal. Validar os códigos e as unidades com o XML autorizado e a contabilidade antes da carga.

## Critérios para o ensaio

1. Identificar o Red Velvet e resolver os três itens fora do menu conforme a operação deseja vendê-los; conferir os nomes e as unidades com a equipe.
2. Obter do sistema atual/contador os dados vigentes do emitente, classificações dos cinco produtos e série/próximo número **por ambiente**. A série vista neste papel é apenas evidência histórica.
3. Na homologação, fechar uma comanda de cinco linhas, R$ 88,00 e pagamento por cartão externo; comparar total, unidade, nome e forma de pagamento no XML/DANFE/QR gerados pelo Farelo OS. Usar dados de consumidor de teste, nunca os da foto.
4. Só considerar concluído após autorização real em homologação, revisão fiscal e inspeção do DANFE impresso. Não deduzir capacidade de emissão pela existência desta nota do sistema antigo.
