# Decisões fiscais pendentes — cardápio QR público

Fonte: `sischef-extraction/fiscal-candidate-crosswalk-20261023.md` (análise de 23/10/2026). Este arquivo contém somente itens de catálogo e evidência histórica; não contém clientes, certificados ou senhas. Nenhum valor abaixo está aprovado para emissão de NFC-e.

## Decisões comuns aos 39 itens

1. Confirmar o vínculo pelo ID do Sischef e o produto/variante comercial efetivo; alta confiança não dispensa conferência.
2. Conferir NCM, CEST quando aplicável, CFOP, CSOSN/CST, origem ICMS e PIS/COFINS com documentação do produto e a contabilidade. Distinguir cadastro histórico de código efetivamente usado em XML.
3. Informar GTIN comercial válido ou `SEM GTIN` explicitamente, além da unidade comercial fiscal. O código interno do Sischef não comprova GTIN.
4. Conferir preço final e passos/opções; perfil fiscal e emitente preenchidos não são autorização da SEFAZ.

Os 13 itens adicionais do painel autenticado do QR Pedir ainda não fazem parte deste cruzamento.

## Vínculo de cada item a resolver

| QR ID | Produto | Vínculo | Decisão específica |
| --- | --- | --- | --- |
| 147974 | bolo de festa Ninho com Morango | SEM VÍNCULO | Encontrar cadastro equivalente ou classificar individualmente com a contabilidade. |
| 119254 | Pão na Chapa com Ovos Mexidos e Bacon | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 88832 | Queijo Quente com Molho Pesto | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57773 | Misto Quente | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 75387 | Pão na Chapa Com Manteiga | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 77443 | Omelete Recheado com Presunto e Queijo | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 57771 | Cestinha de mini pães de queijo | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 110445 | Suco de Laranja INTEGRAL | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 71915 | Café Gelado Artesanal | AMBÍGUO | Escolher o ID/variante correto na ficha do Sischef; não copiar códigos ainda. |
| 57758 | Água com gás | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57749 | Água mineral | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57759 | Água de Coco | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57766 | Refrigerantes | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 172925 | Água Tônica Antárctica | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 172924 | Energético Red Bull Tradicional | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 156811 | SODA ITALIANA DE BLUEBERRY | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 156810 | SODA ITALIANA DE PINK LEMONADE | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 131000 | Café Coado Curto | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 130999 | Café Coado Longo | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 57750 | Café expresso | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57751 | Café expresso longo(carioca) | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 72851 | Café com Leite (Pingado) | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 73035 | Achocolatado Quente | AMBÍGUO | Escolher o ID/variante correto na ficha do Sischef; não copiar códigos ainda. |
| 110685 | Mocaccino | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57752 | Cappuccino Com Canela | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 119788 | Cappuccino Com Chocolate | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 119789 | Leite Quente | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 142815 | Bolo de Laranja | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 130998 | Bolo Gelado de Abacaxi com Coco | SEM VÍNCULO | Encontrar cadastro equivalente ou classificar individualmente com a contabilidade. |
| 72061 | Brownie com cobertura de Doce de Leite | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 173809 | Bolo Gelado Surpresa de Uva | AMBÍGUO | Escolher o ID/variante correto na ficha do Sischef; não copiar códigos ainda. |
| 173707 | Pirulito 7 Belo | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 57779 | Cookie | ALTA | Confirmar ID candidato e confrontar códigos com documento/contador. |
| 57777 | Muffin de Frutas Zero Açucar | AMBÍGUO | Escolher o ID/variante correto na ficha do Sischef; não copiar códigos ainda. |
| 58022 | Muffin Vegano de Chocolate (contém açúcar, sem glúten) | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 57768 | Cerveja | MÉDIA | Resolver divergência de nome, variante, grupo ou preço antes de usar candidato. |
| 87111 | Picolé de Chocolate | SEM VÍNCULO | Encontrar cadastro equivalente ou classificar individualmente com a contabilidade. |
| 87108 | Picolé de Limão | SEM VÍNCULO | Encontrar cadastro equivalente ou classificar individualmente com a contabilidade. |
| 91085 | Picolé de Uva | SEM VÍNCULO | Encontrar cadastro equivalente ou classificar individualmente com a contabilidade. |

Valores de NCM/CEST/CFOP/CSOSN e a evidência por XML estão preservados em `fiscal-candidates-20261023.json` e visíveis em `/admin/fiscal-review`.
