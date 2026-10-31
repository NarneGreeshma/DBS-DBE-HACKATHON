"""SmartHire :: drop and recreate the application databases.

Used during development when a migration file changes (Flyway refuses to re-run an
already-applied version).  Run with:  py scripts/db-reset.py
"""
import sys

import psycopg

MAINT = "host=127.0.0.1 port=5432 dbname=postgres user=smarthire connect_timeout=5"
DBS = ["smarthire", "smarthire_test"]


def main() -> int:
    conn = psycopg.connect(MAINT, autocommit=True)
    cur = conn.cursor()

    # terminate open sessions so DROP DATABASE succeeds
    cur.execute(
        """
        select pg_terminate_backend(pid)
        from pg_stat_activity
        where datname = any(%s) and pid <> pg_backend_pid()
        """,
        (DBS,),
    )

    for name in DBS:
        cur.execute(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)')
        cur.execute(f'CREATE DATABASE "{name}"')
        print(f"recreated {name}")

    conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
