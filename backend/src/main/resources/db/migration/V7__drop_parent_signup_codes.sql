-- 학부모 가입 코드를 없앤다. 학부모 계정은 학생 가입·선생님 등록 시점에
-- 보호자 번호로 바로 만들어진다(초기 비밀번호 0000, must_change_password=true).
--
-- 남아 있는 미사용 PARENT 코드는 이제 쓸 곳이 없다. 코드 입력 화면이 학생 전용이 되어
-- 넣어도 거부되므로, 학부모가 들고 있어 봐야 "안 되는 코드"만 된다.
DELETE FROM signup_codes WHERE target_role = 'PARENT';

-- 앞으로 PARENT 코드는 발급하지 않는다. 제약으로 못 박아 두면 실수로 되살아나지 않는다.
-- 되돌리려면 이 제약을 풀고 SignupService·StudentService의 발급 경로를 함께 되살려야 한다.
ALTER TABLE signup_codes DROP CONSTRAINT ck_signup_codes_role;
ALTER TABLE signup_codes ADD CONSTRAINT ck_signup_codes_role
    CHECK (target_role = 'STUDENT');
