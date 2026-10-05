# -*- coding: utf-8 -*-
"""备份恢复演练：把系统备份 ZIP 恢复到独立 schema 并校验行数。

用法:
  py -X utf8 backup_restore_drill.py --zip backup_latest.zip
  py -X utf8 backup_restore_drill.py --from-storage          # 自动下载最新备份
  py -X utf8 backup_restore_drill.py --zip xx.zip --schema restore_drill --keep

防呆: 目标 schema 不允许为 public；默认演练后删除 schema（--keep 保留供人工检查）。
"""
import argparse, io, json, os, re, sys, zipfile

import psycopg2

SUPABASE = {
    'storage': 'https://lzdhsdlnrkwapspaogwm.supabase.co/storage/v1/object/contract-attachments/',
    'service_key': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx6ZGhzZGxucmt3YXBzcGFvZ3dtIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NzIwNjY2MywiZXhwIjoyMTAyNzgyNjYzfQ.NbVmCvMJl_oBhY-q2sZyuAufLS0bDnFI3U-75pWVix4',
}
DB = {'host': 'aws-0-ap-southeast-1.pooler.supabase.com', 'port': 5432, 'dbname': 'postgres',
      'user': 'postgres.lzdhsdlnrkwapspaogwm', 'password': 'ibQmHFYKZNs4hfSj'}


def camel_to_snake(name: str) -> str:
    return re.sub(r'([A-Z])', r'_\1', name).lower()


def load_conn():
    return psycopg2.connect(connect_timeout=30, sslmode='require', **DB)


def latest_backup_path() -> str:
    import urllib.request, ssl
    base = os.environ.get('API_BASE', 'https://auditing-management-system.onrender.com/api')
    ctx = ssl.create_default_context()
    rq = urllib.request.Request(base + '/auth/login', data=json.dumps(
        {'username': 't_admin@firm.cn', 'password': 'Test@123456'}).encode(), method='POST')
    rq.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(rq, context=ctx, timeout=120) as r:
        token = json.loads(r.read())['data']['token']
    rq = urllib.request.Request(base + '/system/backup/history')
    rq.add_header('Authorization', 'Bearer ' + token)
    with urllib.request.urlopen(rq, context=ctx, timeout=120) as r:
        h = json.loads(r.read())['data']
    if not h:
        raise SystemExit('备份历史为空')
    return h[0]['objectPath']


def fetch_zip_from_storage(object_path: str) -> bytes:
    import urllib.request, ssl
    ctx = ssl.create_default_context()
    rq = urllib.request.Request(SUPABASE['storage'] + object_path)
    rq.add_header('Authorization', 'Bearer ' + SUPABASE['service_key'])
    with urllib.request.urlopen(rq, context=ctx, timeout=600) as r:
        return r.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--zip', help='本地备份 ZIP 路径')
    ap.add_argument('--from-storage', action='store_true', help='自动下载最新备份')
    ap.add_argument('--schema', default='restore_drill')
    ap.add_argument('--keep', action='store_true', help='演练后保留 schema')
    args = ap.parse_args()
    if args.schema == 'public' or not args.schema:
        raise SystemExit('拒绝：目标 schema 不能是 public')

    # 1. 取 ZIP
    if args.zip:
        data = open(args.zip, 'rb').read()
        print(f'使用本地备份: {args.zip} ({len(data)/1024:.0f} KB)')
    elif args.from_storage:
        obj = latest_backup_path()
        print('下载最新备份:', obj)
        data = fetch_zip_from_storage(obj)
        print(f'下载完成 ({len(data)/1024:.0f} KB)')
    else:
        raise SystemExit('请指定 --zip 或 --from-storage')

    # 2. 解析表 JSON
    z = zipfile.ZipFile(io.BytesIO(data))
    tables = {}
    for name in z.namelist():
        if name.endswith('.json') and 'manifest' not in name:
            table = name.split('/')[-1].replace('.json', '')
            tables[table] = json.loads(z.read(name).decode('utf-8'))
    files = [n for n in z.namelist() if not n.endswith('.json')]
    print(f'备份含 {len(tables)} 张表数据、{len(files)} 个附件文件')

    conn = load_conn()
    conn.autocommit = True
    cur = conn.cursor()
    schema = args.schema
    cur.execute(f'DROP SCHEMA IF EXISTS {schema} CASCADE')
    cur.execute(f'CREATE SCHEMA {schema}')

    ok_tables, fail_tables = [], []
    for table, rows in sorted(tables.items()):
        if not isinstance(rows, list):
            continue
        # 先 LIKE 复制 public 表结构，再取目标列集合
        cur.execute(f'CREATE TABLE {schema}.{table} (LIKE public.{table} INCLUDING ALL)')
        cur.execute("""SELECT column_name FROM information_schema.columns
                       WHERE table_schema=%s AND table_name=%s""", (schema, table))
        cols = {r[0] for r in cur.fetchall()}
        inserted = 0
        for row in rows:
            pairs = []
            for k, v in (row or {}).items():
                col = camel_to_snake(k)
                if col in cols:
                    pairs.append((col, v))
            if not pairs:
                continue
            col_list = ','.join(c for c, _ in pairs)
            ph = ','.join(['%s'] * len(pairs))
            try:
                cur.execute(f'INSERT INTO {schema}.{table} ({col_list}) VALUES ({ph})',
                            [v for _, v in pairs])
                inserted += 1
            except Exception as e:
                fail_tables.append((table, str(e)[:80]))
                conn.rollback()
                conn.autocommit = True
        # 行数校验
        cur.execute(f'SELECT count(*) FROM {schema}.{table}')
        actual = cur.fetchone()[0]
        if actual == len(rows):
            ok_tables.append((table, actual))
            print(f'  ✓ {table}: {actual}/{len(rows)} 行一致')
        else:
            fail_tables.append((table, f'行数不符 {actual}/{len(rows)}'))
            print(f'  ✗ {table}: {actual}/{len(rows)} 行不符')

    print()
    print(f'=== 恢复演练结果: 成功 {len(ok_tables)} 表, 异常 {len(fail_tables)} 表 ===')
    for t, why in fail_tables:
        print('  异常:', t, why)
    if args.keep:
        print(f'schema {schema} 已保留供人工检查')
    else:
        cur.execute(f'DROP SCHEMA {schema} CASCADE')
        print(f'schema {schema} 已清理')
    conn.close()
    sys.exit(1 if fail_tables else 0)


if __name__ == '__main__':
    main()
