# Prepares the dev server for a demo: removes ONLY the demo accounts' data, then registers them again.
# Other accounts in the dev database (e.g. your own) are never touched.
#
#   powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1
#
# Needs: docker compose up -d (PostgreSQL) and the server running (./gradlew :server:run).

$ErrorActionPreference = "Stop"
$Server = if ($env:CHAT_SERVER) { $env:CHAT_SERVER } else { "http://localhost:8080" }
# Test accounts for the demo (test values only — not real credentials).
$DemoPassword = "demopass1"
$DemoUsers = @(
    @{ username = "nhan";  displayName = "Nhân Nguyễn" },
    @{ username = "ptien"; displayName = "P Tiến" },
    @{ username = "pminh"; displayName = "P Minh" }
)

try { $health = Invoke-RestMethod "$Server/health" -TimeoutSec 10 } catch { throw "Server not reachable at $Server — start it first (./gradlew :server:run)." }
if ($health.db -ne "up") { throw "Server is up but the database is down — run: docker compose up -d" }

$names = ($DemoUsers | ForEach-Object { "'" + $_.username + "'" }) -join ","
# Demo data = the accounts above + the guest accounts the app creates by itself on each fresh install.
function Demo($alias) { "($alias.username IN ($names) OR $alias.username LIKE 'guest\_%')" }
$sql = @"
\set ON_ERROR_STOP on
BEGIN;
-- Refuse to touch a conversation that also has a non-demo member (its messages reference the demo users).
DO `$`$ BEGIN
  IF EXISTS (
    SELECT 1 FROM conversation_members cm JOIN users u ON u.id = cm.user_id
    WHERE $(Demo 'u') AND EXISTS (
      SELECT 1 FROM conversation_members o JOIN users ou ON ou.id = o.user_id
      WHERE o.conversation_id = cm.conversation_id AND NOT $(Demo 'ou'))
  ) THEN RAISE EXCEPTION 'A demo account shares a conversation with a non-demo account: reset aborted, nothing deleted.';
  END IF;
END `$`$;
CREATE TEMP TABLE demo_conv AS
  SELECT DISTINCT cm.conversation_id AS id FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE $(Demo 'u');
DELETE FROM inbox WHERE conversation_id IN (SELECT id FROM demo_conv) OR user_id IN (SELECT id FROM users u WHERE $(Demo 'u'));
DELETE FROM messages WHERE conversation_id IN (SELECT id FROM demo_conv);
DELETE FROM conversation_members WHERE conversation_id IN (SELECT id FROM demo_conv);
DELETE FROM conversations WHERE id IN (SELECT id FROM demo_conv);
DELETE FROM users u WHERE $(Demo 'u');
COMMIT;
"@
$sql | docker exec -i mobilechat-postgres psql -q -U chat -d chat
if ($LASTEXITCODE -ne 0) { throw "Database reset failed (see the message above). Nothing was deleted." }
Write-Host "Removed old demo data for: $names and guest_* accounts"

foreach ($u in $DemoUsers) {
    $body = @{ username = $u.username; displayName = $u.displayName; password = $DemoPassword } | ConvertTo-Json
    Invoke-RestMethod "$Server/api/auth/register" -Method Post -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
    Write-Host ("Registered {0,-6} ({1})" -f $u.username, $u.displayName)
}
Write-Host "Demo accounts ready. Password for all: $DemoPassword"
