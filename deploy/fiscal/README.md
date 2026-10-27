# Custódia do certificado A1

O A1 fica cifrado na VPS em `/opt/farelo-os/shared/fiscal/a1.p12.cred`, com
permissão `600`, dentro de diretório `700`. O arquivo original é transmitido
pelo SSH diretamente para `systemd-creds encrypt`; nenhum P12 simples é salvo
na VPS. A credencial é vinculada à chave local do host e protegida por
AES-256-GCM. O instalador compara os bytes após descriptografar por um canal
SSH, sem registrar nem gravar o conteúdo. O original permanece no Mac.

No Mac, para uma instalação nova:

```bash
bash deploy/fiscal/install-a1-encrypted.sh '/caminho/do/certificado.p12'
```

O script recusa substituir a credencial existente. A senha **não** deve ir ao
Git, chat, argumento de processo nem `.env`. Quando o proprietário estiver no
Terminal do Mac, cadastrá-la interativamente com:

```bash
bash deploy/fiscal/enroll-a1-password.sh
```

Esse segundo script também recusa substituição e guarda somente
`a1-password.cred` cifrado. Depois, na VPS, executar
`bash /opt/farelo-os/current/deploy/fiscal/check-a1-credentials.sh` para
confirmar A1 e senha por um processo transitório. Não mostrar o conteúdo dos
arquivos nem executar `systemd-creds decrypt` com saída no terminal.
O P12 atual usa RC2 internamente; por isso o verificador usa a opção
[`-legacy`](https://docs.openssl.org/3.5/man1/openssl-pkcs12/) do OpenSSL 3.
Isso não altera a proteção externa da credencial cifrada pelo systemd.

O PDV e seus contêineres **não** montam essas credenciais. Quando a emissão
fiscal estiver aprovada, um serviço fiscal separado deverá receber as duas
credenciais por `LoadCredentialEncrypted=`, disponibilizadas só no diretório
temporário `$CREDENTIALS_DIRECTORY` durante sua execução. O identificador
deve ser `a1.p12.cred` (e `a1-password.cred`), igual ao nome embutido na
credencial; outro identificador é recusado pelo systemd. O processo fiscal
deve falhar fechado se faltar A1, senha ou classificação, enquanto o PDV e o
banco continuam ativos. Nada aqui habilita transmissão de NFC-e.

A VPS atual não possui TPM nem criptografia de disco para a chave do host. A
chave `/var/lib/systemd/credential.secret` fica acessível a root (`400`):
essa solução protege o A1 armazenado isoladamente, mas **não** contra um
invasor com acesso root ou cópia completa do disco e da chave. Ela permite
reinício sem intervenção humana, evitando depender do Mac para cada emissão.
Para recuperação da VPS, conservar o P12 original e sua senha fora do servidor;
uma cópia da credencial cifrada sozinha não basta sem a chave do host. Não
copiar a chave do host para o Git ou para backups sem proteção própria.

Referências: [systemd-creds](https://www.freedesktop.org/software/systemd/man/255/systemd-creds.html),
[systemd.exec credentials](https://www.freedesktop.org/software/systemd/man/255/systemd.exec.html#Credentials),
[Docker Compose secrets](https://docs.docker.com/reference/compose-file/secrets/).
