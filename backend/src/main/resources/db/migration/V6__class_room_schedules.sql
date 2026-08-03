-- 반 하나가 요일 슬롯을 여러 개 갖는다. class_rooms의 day_of_week/start_time을 대체한다.
--
-- uq_crs_day가 (class_room_id, day_of_week)인 이유: lessons가 날짜당 1행이다.
-- 같은 요일에 슬롯 두 개를 허용하면 일괄 생성이 그 날짜에 수업을 하나만 만들고
-- 두 번째 슬롯은 에러 없이 사라진다. 지킬 수 없는 데이터를 애초에 막는다.
--
-- 이 테이블의 id는 안정적이지 않다. 수정이 통째 교체(DELETE 후 INSERT)라
-- 다른 테이블에서 이 id를 참조하지 마라.
CREATE TABLE class_room_schedules (
    id            BIGSERIAL PRIMARY KEY,
    class_room_id BIGINT      NOT NULL REFERENCES class_rooms(id) ON DELETE CASCADE,
    day_of_week   SMALLINT    NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_crs_dow  CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_crs_time CHECK (end_time IS NULL OR end_time > start_time),
    CONSTRAINT uq_crs_day  UNIQUE (class_room_id, day_of_week)
);

CREATE INDEX idx_crs_class ON class_room_schedules(class_room_id);

-- 기존 반 이관. end_time은 채울 근거가 없어 NULL로 둔다 --
-- start_time + 2시간 같은 값을 지어내면 틀린 시각이 그럴듯하게 보인다.
INSERT INTO class_room_schedules (class_room_id, day_of_week, start_time)
SELECT id, day_of_week, start_time
FROM class_rooms
WHERE day_of_week IS NOT NULL AND start_time IS NOT NULL;

-- term_start/term_end는 어떤 로직도 읽지 않았다. 수업 일괄 생성은 요청의 from/to를 쓴다.
ALTER TABLE class_rooms
    DROP COLUMN day_of_week,
    DROP COLUMN start_time,
    DROP COLUMN term_start,
    DROP COLUMN term_end;
