#!/usr/bin/env python3
"""Create an ephemeral pgvector stack, run all migrations and tests, then remove it."""
import pathlib
import subprocess
import uuid
root = pathlib.Path(__file__).resolve().parents[2]
project = 'di-vector-test-' + uuid.uuid4().hex[:8]
base = ['docker', 'compose', '-p', project, '-f', 'compose.vector-test.yaml']
def run(*args):
    subprocess.run([*base, *args], cwd=root, check=True)
try:
    run('run', '--rm', 'database-bootstrap')
    run('run', '--rm', '--no-deps', 'backend')
    run('exec', '-T', 'database', 'mkdir', '-p', '/tmp/verification')
    run('cp', 'tools/database/.', 'database:/tmp/verification')
    run('exec', '-T', 'database', 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'dependency', '-d', 'dependency_validation', '-f', '/tmp/verification/verify.sql')
    (root / 'docs/database').mkdir(parents=True, exist_ok=True)
    with (root / 'docs/database/schema-catalog.json').open('w') as output:
        subprocess.run([*base, 'exec', '-T', 'database', 'psql', '-X', '-A', '-t',
            '-U', 'dependency', '-d', 'dependency_validation', '-f', '/tmp/verification/catalog.sql'],
            cwd=root, check=True, stdout=output)
    print('PASS: optional vector migrations, model dimensions and full SQL suite')
finally:
    subprocess.run([*base, 'down', '--volumes'], cwd=root, check=True)
