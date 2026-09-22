import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const schemaIn = fs.readFileSync(path.join(root, 'database/schema.sql'), 'utf8')
const seedIn = fs.readFileSync(path.join(root, 'database/seed-data.sql'), 'utf8')

function convertSchema(src) {
  let s = src.replace(/CREATE DATABASE[\s\S]*?USE haofangzi;\s*/i, '')
  s = s.replace(/\/\*[\s\S]*?\*\//g, '')
  const tables = []
  const indexes = []
  const views = []
  const re = /CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\)\s*ENGINE=InnoDB[^;]*;/g
  let m
  while ((m = re.exec(s))) {
    const name = m[1]
    let body = m[2]
    body = body.replace(/COMMENT\s+'[^']*'/g, '')
    body = body.replace(/active_flag\s+TINYINT\s+GENERATED ALWAYS AS \(IF\(`status` = 'ACTIVE', 1, NULL\)\) STORED/g,
      "active_flag INTEGER GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED")
    body = body.replace(/\bBIGINT\b/g, 'INTEGER')
    body = body.replace(/\bTINYINT\b/g, 'INTEGER')
    body = body.replace(/\bINT\b/g, 'INTEGER')
    body = body.replace(/DECIMAL\(\d+,\s*\d+\)/g, 'REAL')
    body = body.replace(/\bJSON\b/g, 'TEXT')
    body = body.replace(/VARCHAR\(\d+\)/g, 'TEXT')
    body = body.replace(/DATETIME\(\d+\)/g, 'TEXT')
    body = body.replace(/\s+AUTO_INCREMENT/g, '')
    body = body.replace(/\s+ON UPDATE CURRENT_TIMESTAMP/g, '')
    body = body.replace(/,\s*PRIMARY KEY \(id\)/g, '')
    body = body.replace(/UNIQUE KEY \w+\s+/g, 'UNIQUE ')
    body = body.replace(/,\s*KEY \w+\s+\([^)]+\)/g, '')
    body = body.replace(/^\s*id\s+INTEGER\s+NOT NULL\s*,/m, '  id INTEGER PRIMARY KEY,')
    body = body.replace(/,\s*,/g, ',')
    body = body.replace(/,\s*\)/g, '\n)')
    tables.push(`CREATE TABLE ${name} (\n${body.trim()}\n);`)
  }
  const viewRe = /CREATE OR REPLACE VIEW (\w+) AS\s*([\s\S]*?);/g
  while ((m = viewRe.exec(s))) {
    let body = m[2]
    body = body.replace(/SUM\(([\w.]+)='([^']+)'\)/g, "SUM(CASE WHEN $1='$2' THEN 1 ELSE 0 END)")
    body = body.replace(/SUM\((status) IN \(([^)]+)\)\)/g, 'SUM(CASE WHEN $1 IN ($2) THEN 1 ELSE 0 END)')
    body = body.replace(/SUM\(status='([^']+)'\)/g, "SUM(CASE WHEN status='$1' THEN 1 ELSE 0 END)")
    views.push(`CREATE VIEW ${m[1]} AS\n${body.trim()};`)
  }
  return ['PRAGMA foreign_keys = ON;', ...tables, ...indexes, ...views].join('\n\n') + '\n'
}

function convertSeed(src) {
  let s = src.replace(/^USE haofangzi;\s*/m, '')
  const cut = s.indexOf('DROP PROCEDURE')
  if (cut > 0) s = s.slice(0, cut)
  s = s.replace(/DATE_ADD\(NOW\(\), INTERVAL 10 MINUTE\)/g, "datetime('now','localtime','+10 minutes')")
  s = s.replace(/DATE_ADD\(CURDATE\(\), INTERVAL 1 DAY\)/g, "date('now','localtime','+1 day')")
  s = s.replace(/CONCAT\('\{"houseId":', h\.id, ',"area":', h\.area, '\}'\)/g,
    `'{"houseId":' || h.id || ',"area":' || h.area || '}'`)
  s = s.replace(/UPDATE hf_house h SET h\.sale_status = 'LOCKED', h\.lock_expire_at/g,
    "UPDATE hf_house SET sale_status = 'LOCKED', lock_expire_at")
  s = s.replace(/WHERE h\.floor_no/g, 'WHERE floor_no')
  return s.trim() + '\n'
}

const outDir = path.join(root, 'backend/src/main/resources/sqlite')
fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(path.join(outDir, 'schema.sql'), convertSchema(schemaIn))
fs.writeFileSync(path.join(outDir, 'seed.sql'), convertSeed(seedIn))
console.log('wrote sqlite schema and seed')
