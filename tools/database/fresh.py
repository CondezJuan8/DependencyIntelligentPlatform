#!/usr/bin/env python3
"""Validate Flyway from zero on an isolated DB; preserve the developer database."""
import pathlib
import subprocess
import uuid

root = pathlib.Path(__file__).resolve().parents[2]
name = 'di_validation_' + uuid.uuid4().hex[:12]

def compose(*args, **kwargs):
    return subprocess.run(['docker', 'compose', *args], cwd=root, check=True, **kwargs)

def admin(sql):
    return compose('exec', '-T', 'database', 'sh', '-c',
        'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres', input=sql, text=True)

try:
    admin(f'CREATE DATABASE {name};')
    compose('run', '--rm', '-e', f'PGDATABASE={name}', '-e', f'POSTGRES_DB={name}', 'database-bootstrap')
    compose('run', '--rm', '--no-deps', '-e', f'POSTGRES_DB={name}',
        '-e', 'SPRING_PROFILES_ACTIVE=future', 'backend',
        'java', '-jar', 'app.jar', '--spring.main.web-application-type=none')
    compose('exec', '-T', 'database', 'mkdir', '-p', '/tmp/di-database-verification')
    compose('cp', 'tools/database/.', 'database:/tmp/di-database-verification')
    compose('exec', '-T', 'database', 'sh', '-c',
        'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -f /tmp/di-database-verification/verify.sql', 'sh', name)
    print('PASS: clean Flyway install and full SQL suite')
finally:
    admin(f'DROP DATABASE IF EXISTS {name} WITH (FORCE);')
