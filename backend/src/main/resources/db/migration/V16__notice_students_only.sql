-- 공지에 「학생만 보기」. 켜면 학부모 목록·상세·홈 배너에서 그 공지가 빠진다.
--
-- 기본값이 false인 이유가 둘이다. 기존 공지의 동작이 바뀌지 않아야 하고,
-- 수업일·클리닉 변경이 자동 발행하는 STUDENT scope 공지가 학부모에게 그대로 가야 한다
-- (학부모가 못 받으면 변경 알림 자체가 무의미하다).
ALTER TABLE notices ADD COLUMN students_only BOOLEAN NOT NULL DEFAULT false;
