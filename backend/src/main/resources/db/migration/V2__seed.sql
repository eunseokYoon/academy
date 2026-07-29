-- 시드는 선생님 계정 하나뿐이다. 학교·학년 개념이 없어 넣을 기준 데이터가 없다.
-- 운영 순서: 반 생성 → 코드 배포 → 학생 가입

-- 선생님 계정
-- login_id는 전화번호다. password_hash는 BCrypt('0000').
-- TODO: 실제 강사 번호를 받으면 V4__update_teacher.sql로 교체할 것.
--       01000000000으로 두면 운영에서 로그인할 수 없다.
INSERT INTO users (role, login_id, password_hash, name, phone)
VALUES ('TEACHER', '01000000000',
        '$2a$10$bpAuBat3V6nK6YLWA6.Jhe1eIFo7zRFht1BFt4ZxUYodFsZ7BPU6y',
        '이관우', '01000000000');

INSERT INTO teachers (user_id)
SELECT id FROM users WHERE login_id = '01000000000';
