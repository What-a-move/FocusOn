-- V19의 checksum과 기존 결과를 보존한다. 새 쓰기는 강화하고 기존 불량 결과는 조회에서 차단한다.
CREATE FUNCTION learning_summary_nonblank(value TEXT) RETURNS BOOLEAN
LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT translate(value,
        U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000\FEFF',
        '') <> ''
$$;
-- 기존 length 함수의 의미는 유지해 과거 행과 기존 CHECK를 변경하지 않는다.
CREATE FUNCTION learning_summary_concepts_nonblank(concepts JSONB) RETURNS BOOLEAN
LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT coalesce(bool_and(coalesce(
        learning_summary_nonblank(item->>'name')
        AND learning_summary_nonblank(item->>'briefExplanation'), false)), true)
    FROM jsonb_array_elements(CASE WHEN jsonb_typeof(concepts) = 'array'
        THEN concepts ELSE '[]'::jsonb END) AS item
$$;
-- 과거 READY 행을 삭제/수정하거나 시작을 막지 않는다. 정리 후 별도 VALIDATE가 필요하다.
ALTER TABLE learning_summaries ADD CONSTRAINT ck_learning_summary_nonblank CHECK (
    summary_status <> 'READY' OR (
        overview IS NOT NULL AND learning_summary_nonblank(overview)
        AND next_start_prompt IS NOT NULL AND learning_summary_nonblank(next_start_prompt)
        AND learning_summary_concepts_nonblank(key_concepts)
    )
) NOT VALID;
