import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  fetchTeacherQnaList,
  fetchTeacherReviews,
  listClassRooms,
  markTeacherQnaSeen,
} from "../api";
import { PageTitle } from "../../../shared/components/Section";
import { StarRating } from "../../../shared/review/StarRating";

type Tab = "QUESTION" | "REVIEW";

/**
 * T-15 질의응답 목록. 비공개 질문도 전부 나온다.
 *
 * <p><b>미답변 개수나 상태 뱃지를 만들지 마라.</b> 확인이라는 행위를 두지 않기로 확정했다 —
 * 숙제 피드백·확인 단계를 V12에서 없앤 것과 같은 이유다.
 *
 * <p>질문과 수강 후기를 탭으로 가른다. 기본은 질문이다.
 *
 * <p><b>후기 탭에 답글·상태 뱃지를 만들지 마라.</b> 후기에는 답글이 없다.
 * 질문 탭의 「답글 N」 뱃지를 그대로 가져다 쓰면 안 된다.
 */
export default function QnaListPage() {
  const [classRoomId, setClassRoomId] = useState<number | undefined>(undefined);
  const [tab, setTab] = useState<Tab>("QUESTION");
  const queryClient = useQueryClient();
  /** 이 화면에 들어오기 전 마지막으로 연 시각. 그 뒤의 질문에 「새 글」을 붙인다 */
  const [seenBefore, setSeenBefore] = useState<string | null>(null);

  // 들어오면 한 번 「봤다」고 알린다. 대시보드의 새 질문 수가 0이 된다.
  // 목록 GET 에 붙이지 않은 이유는 반을 바꿀 때마다 다시 불려서다
  // 한 번만 부른다 — 두 번 부르면 두 번째 응답의 「앞 시각」이 방금 첫 호출이 찍은 시각이라
  // 새 글 표시가 전부 사라진다(개발 모드의 StrictMode 가 effect 를 두 번 돌린다)
  const marked = useRef(false);
  useEffect(() => {
    if (marked.current) return;
    marked.current = true;
    markTeacherQnaSeen()
      .then((res) => {
        setSeenBefore(res.previousSeenAt);
        return queryClient.invalidateQueries({ queryKey: ["teacher", "dashboard"] });
      })
      .catch(() => {
        // 실패해도 목록은 보인다. 새 글 표시와 대시보드 숫자만 그대로다
      });
  }, [queryClient]);

  const { data: classRooms } = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const { data: page, isLoading } = useQuery({
    queryKey: ["teacher", "qna", classRoomId],
    queryFn: () => fetchTeacherQnaList({ classRoomId }),
  });

  const { data: reviews, isLoading: reviewsLoading } = useQuery({
    queryKey: ["teacher", "reviews", classRoomId],
    queryFn: () => fetchTeacherReviews({ classRoomId }),
    enabled: tab === "REVIEW",
  });

  return (
    <div className="space-y-4">
      <PageTitle
        action={
          <select
            value={classRoomId ?? ""}
            onChange={(e) =>
              setClassRoomId(e.target.value === "" ? undefined : Number(e.target.value))
            }
            /* 360px에서 긴 반 이름이 제목을 밀어내지 않도록 폭을 묶는다 */
            className="max-w-[45%] shrink rounded-lg border border-brand-200 p-2 text-sm"
          >
            <option value="">전체 반</option>
            {classRooms?.map((room) => (
              <option key={room.classRoomId} value={room.classRoomId}>
                {room.name}
              </option>
            ))}
          </select>
        }
      >
        질의응답
      </PageTitle>

      {/* 탭 두 개. 자료실 카테고리 필터와 같은 언어 — 선택된 쪽만 bg-brand-900. */}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setTab("QUESTION")}
          className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
            tab === "QUESTION"
              ? "bg-brand-900 text-white"
              : "bg-white text-brand-600 ring-1 ring-brand-100"
          }`}
        >
          질문
        </button>
        <button
          type="button"
          onClick={() => setTab("REVIEW")}
          className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
            tab === "REVIEW"
              ? "bg-brand-900 text-white"
              : "bg-white text-brand-600 ring-1 ring-brand-100"
          }`}
        >
          수강 후기
        </button>
      </div>

      {tab === "QUESTION" && (
        <>
          {isLoading && <p className="text-sm text-brand-500">불러오는 중…</p>}

          {page?.items.length === 0 && (
            <p className="rounded-xl bg-white p-6 text-center text-sm text-brand-500">
              아직 올라온 질문이 없습니다.
            </p>
          )}

          <ul className="space-y-2">
            {page?.items.map((item) => (
              <li key={item.postId}>
                <Link
                  to={`/teacher/qna/${item.postId}`}
                  className="block rounded-xl bg-white p-4 ring-1 ring-brand-100"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="min-w-0 flex-1 truncate font-medium text-brand-900">
                      {/* 문자열로 비교하지 마라 — 시간대 표기(+09:00·Z)나 소수 초가 다르면 틀린다 */}
                      {seenBefore !== null
                        && new Date(item.createdAt).getTime() > new Date(seenBefore).getTime() && (
                        <span className="mr-1.5 rounded-full bg-brand-900 px-1.5 py-0.5 text-[11px]
                                         font-semibold text-white">
                          새 글
                        </span>
                      )}
                      {!item.isPublic && <span aria-label="비공개">🔒 </span>}
                      {item.title}
                    </span>
                    {item.answerCount > 0 && (
                      <span className="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 text-xs
                                       text-brand-600">
                        답글 {item.answerCount}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-brand-500">
                    {item.authorName} · {item.classRoomName}
                    {item.hasPhoto && " · 사진"}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}

      {tab === "REVIEW" && (
        <>
          {reviewsLoading && <p className="text-sm text-brand-500">불러오는 중…</p>}

          {/* 평균이 null이면 "아직 후기가 없습니다"다. 0.0으로 접으면 "별점 0점"으로 읽힌다 */}
          {reviews && reviews.averageRating !== null && (
            <div className="rounded-xl bg-white p-4 ring-1 ring-brand-100">
              <StarRating value={reviews.averageRating} readOnly />
              <p className="mt-1 text-xs text-brand-500">
                평균 {reviews.averageRating.toFixed(1)} · 후기 {reviews.totalCount}건
              </p>
            </div>
          )}

          {reviews && reviews.averageRating === null && (
            <p className="rounded-xl bg-white p-6 text-center text-sm text-brand-500">
              아직 등록된 후기가 없습니다.
            </p>
          )}

          <ul className="space-y-2">
            {reviews?.reviews.items.map((review) => (
              <li key={review.reviewId} className="rounded-xl bg-white p-4 ring-1 ring-brand-100">
                {/* 별은 곁다리다. 카드의 주인공은 본문이라 별을 작게 두고 본문을 키운다 */}
                <StarRating value={review.rating} readOnly />
                <p className="mt-1.5 whitespace-pre-wrap text-[15px] leading-relaxed text-brand-900">
                  {review.content}
                </p>
                <p className="mt-1 text-xs text-brand-500">
                  {review.studentName} · {review.classRoomName ?? "반 미배정"}
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
