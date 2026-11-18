# 기존 Command PostgreSQL에 Order 논리 DB·계정·스키마만 추가합니다. 기존 Product DB/볼륨은 변경하지 않습니다.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $root '.env'
$encoding = New-Object Text.UTF8Encoding($false)
if (-not (Test-Path -LiteralPath $envPath)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    $template = [IO.File]::ReadAllText((Join-Path $root '.env.example'))
    $text = $template.Replace('replace-with-local-generated-secret', $password)
    $stream = [IO.File]::Open($envPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $data = $encoding.GetBytes($text); $stream.Write($data, 0, $data.Length) } finally { $stream.Dispose() }
}
$text = [IO.File]::ReadAllText($envPath)
$match = [regex]::Match($text, '(?m)^DB_PASSWORD=([^\r\n]+)')
if (-not $match.Success -or $text -notmatch '(?m)^DB_USERNAME=orders\r?$') { throw '기존 Order DB 설정을 확인해야 합니다. 덮어쓰지 않습니다.' }
$password = $match.Groups[1].Value
$literal = "'" + $password.Replace("'", "''") + "'"
$sql = "SET log_statement='none';`nSET log_min_error_statement='panic';`nSET standard_conforming_strings=on;`n" + [IO.File]::ReadAllText((Join-Path $root 'docker/postgres/create-order.sql')).Replace(":'orders_password'", $literal)
try {
    # 비밀번호는 표준 입력으로만 전달하며 Docker/psql 출력 원문은 반환하지 않습니다.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $captured = $sql | & docker exec -i stockdrop-command-postgres sh -c 'exec psql -X -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1' 2>&1
    $code = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
    $sql = $null; $literal = $null; $password = $null; $text = $null; $captured = $null
}
if ($code -ne 0) { throw 'Order DB 준비 실패. 기존 DB와 비밀정보를 보존했습니다.' }
Write-Output 'Order DB/role/schema prepared on existing Command PostgreSQL. Secrets withheld.'
