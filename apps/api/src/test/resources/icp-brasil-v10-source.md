# Raiz SSL ICP-Brasil v10 usada nos testes

Arquivo público `icp-brasil-v10.crt` obtido do
[Repositório AC-Raiz do ITI](https://www.gov.br/iti/pt-br/assuntos/repositorio/repositorio-ac-raiz)
em 19/10/2026. A impressão SHA-256 do certificado é
`6e0bff069a26994c15de2c4888cc54af84882e5495b7fbf66be9ccffec7489f6`,
igual à divulgada pelo ITI. O arquivo não contém chave privada.

O cliente de produção recebe uma cópia dessa raiz por caminho externo e
valida a impressão antes de estabelecer TLS. Não usar este arquivo para
contornar erros de certificado: uma falha de validação deve impedir a chamada.
