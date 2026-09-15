-- 대체 등원(MAKEUP) 추가 (2026-09-01 확정).
-- 원래 요일에 못 와서 다른 날 수업에 들어온 경우다. 출석으로 친다 — 결석 쪽이 아니다.
--
-- CHECK 제약이 두 곳이다. 수업 출석(attendances)과 클리닉 출결(clinic_reservations)이
-- 같은 enum을 쓰기 때문이다. 한쪽만 고치면 나머지에서 저장이 조용히 실패한다.
-- 선생님이 고를 수 있는 화면은 수업(T-5)뿐이지만, enum이 공유라 제약은 둘 다 넓힌다.

ALTER TABLE attendances DROP CONSTRAINT ck_attendances_status;
ALTER TABLE attendances ADD CONSTRAINT ck_attendances_status
    CHECK (status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED','MAKEUP'));

ALTER TABLE clinic_reservations DROP CONSTRAINT ck_clinic_res_attend;
ALTER TABLE clinic_reservations ADD CONSTRAINT ck_clinic_res_attend
    CHECK (attend_status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED','MAKEUP'));
