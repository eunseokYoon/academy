-- ============================================================
-- 「다음 수업 예고」를 「수업 숙제」·「클리닉」 둘로 나눈다 (2026-09-10)
--
-- 예고 칸에 실제로 적혀 있던 것은 숙제였다 —
-- "1회: p.144-152 (1,2번 제외) / 2회: p.158-166 / 각 회당 18문항씩 x 2회 = 총 36문항".
-- 한 칸에 숙제와 클리닉 안내가 섞여 있어 학생이 무엇을 해와야 하는지 읽어내야 했다
-- ============================================================
ALTER TABLE lessons ADD COLUMN homework_note TEXT;
ALTER TABLE lessons ADD COLUMN clinic_note   TEXT;

-- 옮긴다. UPDATE가 DROP보다 먼저여야 한다 — 순서를 바꾸면 값이 통째로 사라진다
UPDATE lessons SET homework_note = next_preview WHERE next_preview IS NOT NULL;

ALTER TABLE lessons DROP COLUMN next_preview;
