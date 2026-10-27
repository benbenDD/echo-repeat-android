"""Run the app's actual v5-to-v6 SQL against a populated SQLite database."""
import pathlib
import re
import sqlite3

root = pathlib.Path(__file__).resolve().parents[1]
schema = (root / 'app/src/main/java/com/echoenglish/app/data/DatabaseSchema.kt').read_text(encoding='utf-8')
database = (root / 'app/src/main/java/com/echoenglish/app/data/AppDatabase.kt').read_text(encoding='utf-8')
track_sql = re.search(r'"""(CREATE TABLE tracks .*?)"""', database, re.S).group(1)
bookmark_sql = re.search(r'MIGRATION_2_TO_3 = """(.*?)"""', schema, re.S).group(1)
migration = re.findall(r'"""(.*?)"""', schema.split('val MIGRATION_5_TO_6 =', 1)[1], re.S)
db = sqlite3.connect(':memory:')
db.execute(track_sql)
db.execute(bookmark_sql)
columns = [row[1] for row in db.execute('PRAGMA table_info(tracks)')]
for track_id in (1, 2):
    values = [track_id if name == 'id' else f'{name}-{track_id}' if name in ('audioUri', 'fileName', 'title', 'subtitleUri', 'segmentMode') else 0 for name in columns]
    db.execute(f'INSERT INTO tracks VALUES ({",".join("?" for _ in columns)})', values)
db.execute('INSERT INTO subtitle_bookmarks VALUES (1, 8, 123)')
before = list(db.execute('SELECT * FROM tracks'))
for sql in migration:
    db.execute(sql)
assert len(migration) == 2
assert list(db.execute('SELECT * FROM tracks')) == before
assert list(db.execute('SELECT * FROM subtitle_bookmarks')) == [(1, 8, 123)]
for track_id in (1, 2):
    db.execute('INSERT INTO subtitle_calibration_settings VALUES (?, ?)', (track_id, 'GRADUAL'))
    db.execute('INSERT INTO subtitle_calibration_points VALUES (?, 8, 1000, 1200)', (track_id,))
db.execute('DELETE FROM subtitle_calibration_points WHERE trackId=1')
assert list(db.execute('SELECT trackId, cueId, subtitleTimeMs, audioTimeMs FROM subtitle_calibration_points')) == [(2, 8, 1000, 1200)]
print('PASS: actual v5-to-v6 SQL preserves tracks/bookmarks and isolates calibration by track')
