#!/usr/bin/env bash
# Loads a demo catalogue and two demo accounts into a running development stack.
# Usage: ./development/seed/seed-demo.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if ! docker compose ps --format '{{.Name}}' | grep -q '^mysql$'; then
  echo "The mysql container is not running. Start it with: docker compose up -d mysql" >&2
  exit 1
fi

python3 - "$PWD" <<'PY' > /tmp/shopstack-seed.sql
import base64, pathlib, sys

root = pathlib.Path(sys.argv[1])
products = [
    ("Aurora Wireless Headphones", 129.99, "Over-ear headphones with active noise cancelling, 40 hour battery and USB-C charging.", "Digital Services", "headphones.jpg"),
    ("Nimbus Mechanical Keyboard", 89.50, "75% layout, hot-swappable switches, PBT keycaps and a detachable cable.", "Office Equipment", "keyboard.jpg"),
    ("Terra Ceramic Mug", 18.00, "Hand-thrown stoneware mug, 350 ml, dishwasher safe.", "Household Items", "mug.jpg"),
    ("Lumen Desk Lamp", 45.00, "Warm LED desk lamp with three brightness levels and a weighted base.", "Furniture and Decor", "lamp.jpg"),
    ("Pulse Fitness Band", 59.90, "Heart rate, sleep tracking and a seven day battery.", "Health and Wellness", "band.jpg"),
    ("Atlas Backpack", 74.00, "22 litre backpack in recycled canvas with a padded laptop sleeve.", "Household Items", "backpack.jpg"),
]
names = ",".join(f"'{p[0]}'" for p in products)
print("USE shopstack;")
print("DELETE FROM products_categories WHERE product_id IN (SELECT id FROM products WHERE name IN (%s));" % names)
print("DELETE FROM products WHERE name IN (%s);" % names)
print("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email IN ('seller@demo.shopstack.local','user@demo.shopstack.local'));")
print("DELETE FROM users WHERE email IN ('seller@demo.shopstack.local','user@demo.shopstack.local');")
print("INSERT INTO users (created_at, updated_at, email, username, password) VALUES (NOW(), NOW(), 'seller@demo.shopstack.local', 'seller', '$2b$10$UPpIjbc/zUeBDiS3bxgt7eWJG2DtVKykLCxT8oOOqC71oQuFZ/asS');")
print("SET @seller = LAST_INSERT_ID();")
print("INSERT INTO users (created_at, updated_at, email, username, password) VALUES (NOW(), NOW(), 'user@demo.shopstack.local', 'user', '$2b$10$Mn3Ea8j2pu1YFFk0sAm6MO.dQi/M1wnDhp7huy5qp3SokNueLdrJ6');")
print("SET @buyer = LAST_INSERT_ID();")
print("INSERT INTO user_roles (user_id, role_id) SELECT @seller, id FROM roles WHERE name = 'SELLER';")
print("INSERT INTO user_roles (user_id, role_id) SELECT @buyer, id FROM roles WHERE name = 'USER';")
for name, price, desc, category, image in products:
    blob = base64.b64encode((root / "seed" / "images" / image).read_bytes()).decode()
    print("INSERT INTO products (created_at, updated_at, created_by, updated_by, name, price, description, user_id, photo) "
          "VALUES (NOW(), NOW(), 'seed', 'seed', '%s', %s, '%s', @seller, FROM_BASE64('%s'));" % (name, price, desc, blob))
    print("SET @pid = LAST_INSERT_ID();")
    print("INSERT INTO products_categories (product_id, categories_id) SELECT @pid, id FROM categories WHERE name = '%s';" % category)
PY

docker compose exec -T mysql mysql -uroot -p123 < /tmp/shopstack-seed.sql
rm -f /tmp/shopstack-seed.sql
echo "Demo data loaded. Seller: seller / Demo12345!, buyer: user / Demo12345!"
