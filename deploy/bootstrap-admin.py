#!/usr/bin/env python3
"""One-time interactive setup for the first Farelo PDV administrator."""

import getpass
import subprocess
import sys
import uuid

import bcrypt


USERNAME = "adm.farelodebolo"
PSQL = [
    "docker", "exec", "-i", "farelo-os-db-1", "sh", "-c",
    'psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
]


def query(sql: str) -> str:
    result = subprocess.run(PSQL, input=sql, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def main() -> int:
    existing = query(f"SELECT count(*) FROM app_user WHERE lower(username) = '{USERNAME}';")
    if existing != "0":
        print("Esta conta já existe; nenhuma alteração foi feita.")
        return 1

    password = getpass.getpass("Senha do administrador do PDV: ")
    confirmation = getpass.getpass("Confirme a senha: ")
    if password != confirmation:
        print("As senhas não conferem.", file=sys.stderr)
        return 1
    if not 8 <= len(password.encode("utf-8")) <= 72:
        print("A senha deve ter entre 8 e 72 bytes.", file=sys.stderr)
        return 1

    password_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
    del password, confirmation
    user_id = uuid.uuid4()
    query(f"""
        BEGIN;
        INSERT INTO app_user (id, name, email, username, password_hash, role, active, created_at, updated_at)
        VALUES ('{user_id}', 'Administrador Farelo', NULL, '{USERNAME}', '{password_hash}', 'ADMIN', TRUE, now(), now());
        COMMIT;
    """)
    print(f"Administrador {USERNAME} cadastrado com senha protegida por BCrypt.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.CalledProcessError as error:
        print("Falha ao acessar o banco do PDV. Nenhuma senha foi exibida.", file=sys.stderr)
        sys.exit(error.returncode)
