DESCRIBE TABLE lakehouse.platform.platform_events;

SELECT
  COUNT(*) AS total_rows,
  COUNT(event_id) AS event_id_rows,
  COUNT(event_type) AS event_type_rows,
  COUNT(event_ts) AS event_ts_rows,
  COUNT(value) AS value_rows
FROM lakehouse.platform.platform_events;

SELECT
  event_id,
  event_type,
  event_ts,
  value,
  processed_at,
  kafka_partition,
  kafka_offset
FROM lakehouse.platform.platform_events
ORDER BY processed_at DESC
LIMIT 10;
