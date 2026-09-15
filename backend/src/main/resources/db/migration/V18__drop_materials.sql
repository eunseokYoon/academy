-- 자료실 폐지. 공지 첨부(V17)가 같은 일을 한다.
--
-- 이 드롭은 Material 엔티티를 지우는 커밋과 함께 나가야 한다. 먼저 나가면
-- ddl-auto: validate가 엔티티와 스키마 불일치로 부팅을 막는다.
--
-- S3의 materials/ 객체는 지우지 않는다. 공지 첨부가 같은 프리픽스를 계속 쓴다.
DROP TABLE materials;
