import { Link } from "react-router-dom";
import { dayLabel } from "../../shared/date";
import type { StudentHome } from "./api";

type LastLesson = NonNullable<StudentHome["lastLesson"]>;

/**
 * S-1 홈의 지난 수업 카드. 제목 · 영상 · 수업 내용 · 다음 예고를 한 장에 담는다.
 *
 * <p><b>영상은 여기서 재생하지 않는다.</b> 홈에 iframe을 심으면 첫 화면이 그만큼 무거워지고,
 * 재생은 이미 상세(S-5)가 하는 일이다. 썸네일은 YouTube가 videoId만으로 주는 이미지 한 장이라
 * 그 부담이 없다 — 누르면 상세로 간다. <b>여기에 iframe을 넣지 마라.</b>
 *
 * <p>참고 디자인은 영상 옆에 인물 사진이 있었지만 빼기로 했다. 그 자리를 수업 내용과
 * 다음 예고가 받는다 — 다만 <b>가로로 나란히 두지 않는다.</b> 360px에서 영상이 170px로
 * 줄면 재생 버튼인지 알아볼 수 없다. 영상은 폭을 다 쓰고 글은 그 아래다.
 *
 * <p>내용·예고는 각각 null일 수 있다. 없는 칸은 통째로 숨긴다 — 빈 제목만 남으면
 * 선생님이 안 쓴 것인지 화면이 깨진 것인지 구분되지 않는다.
 */
export function LastLessonCard({ lesson }: { lesson: LastLesson }) {
  return (
    <section className="card overflow-hidden">
      <div className="p-4">
        <p className="tnum eyebrow">
          {lesson.lessonDate.slice(5).replace("-", "월 ")}일 ({dayLabel(lesson.lessonDate)})
        </p>
        <h3 className="mt-1 text-base font-bold tracking-[-0.01em] text-brand-900">
          {lesson.title ?? "지난 수업"}
        </h3>
      </div>

      {/* embedUrl이 null이면 영상이 등록되지 않은 수업이다. 영역을 통째로 숨긴다.
          재생목록은 videoId가 없어 썸네일을 못 그린다 — 아래 img를 건너뛰면
          이미 만들어 둔 404 대비 화면(남색 블록 + 재생 버튼)이 그대로 남는다 */}
      {lesson.embedUrl && (
        <Link
          to={`/student/lessons/${lesson.lessonId}`}
          className="group relative mx-4 flex aspect-video items-center justify-center
                     overflow-hidden rounded-xl bg-brand-950"
        >
          {/*
            YouTube가 videoId만으로 주는 정적 썸네일이다. iframe이 아니라 이미지 한 장이라
            홈이 무거워지지 않는다. 영상이 여러 개면 첫 영상의 썸네일이고, 재생목록
            링크면 videoId가 없으므로 그리지 않는다.

            hqdefault는 480×360(4:3)이라 16:9 칸에 넣으면 위아래 검은 띠가 생긴다.
            object-cover로 그 띠를 잘라 낸다 — mqdefault(320×180)는 비율이 맞는 대신
            폭이 좁아 큰 화면에서 뭉갠다.

            영상이 지워졌거나 비공개면 404다. 그때는 이미지를 숨겨 원래의 남색 블록이
            그대로 남는다 — 깨진 이미지 아이콘이 뜨는 것보다 낫다.
          */}
          {lesson.videoId && (
            <img
              src={`https://i.ytimg.com/vi/${lesson.videoId}/hqdefault.jpg`}
              alt=""
              loading="lazy"
              onError={(e) => {
                e.currentTarget.style.display = "none";
              }}
              className="absolute inset-0 h-full w-full object-cover"
            />
          )}
          {/* 썸네일 위 재생 버튼이 묻히지 않게 살짝 어둡게 깐다 */}
          <span aria-hidden="true" className="absolute inset-0 bg-brand-950/35" />
          <span
            aria-hidden="true"
            className="relative grid h-14 w-14 place-items-center rounded-full bg-black/45
                       ring-1 ring-inset ring-white/40 transition-transform
                       group-active:scale-95"
          >
            {/* 재생 삼각형. 광학적으로 가운데 오게 살짝 오른쪽으로 민다 */}
            <svg viewBox="0 0 24 24" className="ml-0.5 h-6 w-6 fill-white">
              <path d="M8 5v14l11-7z" />
            </svg>
          </span>
          {/* 여러 개면 몇 개인지 알려 준다. 상세에서 골라 볼 수 있다는 신호다 */}
          {lesson.videoCount > 1 && (
            <span className="absolute bottom-2 right-2 rounded-md bg-black/65 px-1.5 py-0.5
                             text-[11px] font-bold text-white">
              영상 {lesson.videoCount}개
            </span>
          )}
          <span className="sr-only">수업영상 보기</span>
        </Link>
      )}

      <div className="space-y-3 p-4">
        {lesson.content && <Block label="수업 내용" text={lesson.content} />}
        {lesson.nextPreview && <Block label="다음 수업 예고" text={lesson.nextPreview} />}

        <Link
          to={`/student/lessons/${lesson.lessonId}`}
          className="block text-xs font-medium text-brand-600 underline"
        >
          수업 레포트 전체 보기
        </Link>
      </div>
    </section>
  );
}

/** 라벨 + 본문 한 덩어리. 줄바꿈은 선생님이 쓴 그대로 살린다. */
function Block({ label, text }: { label: string; text: string }) {
  return (
    <div>
      <p className="eyebrow">{label}</p>
      <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{text}</p>
    </div>
  );
}
