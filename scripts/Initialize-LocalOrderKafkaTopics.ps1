# 공용 로컬 Kafka에 Order 토픽만 준비합니다. 상품 토픽/데이터/소비 위치를 초기화하지 않습니다.
$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\..\ProductQueryService\compose.kafka.yaml'
foreach($topic in @('stockdrop.orders.private.v1','stockdrop.orders.inquiries.private.v1','stockdrop.orders.private.v1.DLT','stockdrop.orders.inquiries.private.v1.DLT','stockdrop.payment.commands.private.v1','stockdrop.payment.commands.private.v1.DLT','stockdrop.payment.results.private.v1','stockdrop.payment.results.private.v1.DLT')) {
    docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 2 --replication-factor 1 --config max.message.bytes=4194304
    if($LASTEXITCODE -ne 0) { throw 'Order 로컬 토픽 준비 실패. 기존 Kafka 데이터를 보존합니다.' }
}
Write-Output 'Order private topics prepared. Production ACL and Order Query consumer are separate work.'
