CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('ADMIN','STAFF','BORROWER')),
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  description TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS equipment (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  category_id INTEGER,
  specification TEXT NOT NULL DEFAULT '',
  total_quantity INTEGER NOT NULL DEFAULT 0 CHECK(total_quantity >= 0),
  available_quantity INTEGER NOT NULL DEFAULT 0 CHECK(available_quantity >= 0),
  location TEXT NOT NULL DEFAULT '',
  notes TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(category_id) REFERENCES categories(id)
);
CREATE TABLE IF NOT EXISTS loans (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  equipment_id INTEGER NOT NULL,
  borrower_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  purpose TEXT NOT NULL DEFAULT '',
  borrowed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_at TEXT,
  returned_at TEXT,
  status TEXT NOT NULL DEFAULT 'BORROWED' CHECK(status IN ('BORROWED','RETURNED')),
  FOREIGN KEY(equipment_id) REFERENCES equipment(id),
  FOREIGN KEY(borrower_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS inventory_checks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  equipment_id INTEGER NOT NULL,
  checker_id INTEGER NOT NULL,
  expected_quantity INTEGER NOT NULL,
  actual_quantity INTEGER NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  checked_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(equipment_id) REFERENCES equipment(id),
  FOREIGN KEY(checker_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS repairs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  equipment_id INTEGER NOT NULL,
  reporter_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  issue TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TEXT,
  FOREIGN KEY(equipment_id) REFERENCES equipment(id),
  FOREIGN KEY(reporter_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_equipment_name ON equipment(name);
CREATE INDEX IF NOT EXISTS idx_loans_status ON loans(status);
CREATE INDEX IF NOT EXISTS idx_loans_borrower ON loans(borrower_id);
CREATE INDEX IF NOT EXISTS idx_repairs_status ON repairs(status);
