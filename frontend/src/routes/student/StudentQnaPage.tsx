import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  createQna,
  createReview,
  deleteReview,
  fetchMyReview,
  fetchQnaList,
  getMe,
  updateReview,
} from "./api";
import { PhotoPicker } from "../../shared/qna/PhotoPicker";
import { PageTitle } from "../../shared/components/Section";
import { StarRating } from "../../shared/review/StarRating";
import type { MyReview } from "../../shared/review/types";
import { errorMessage } from "../../shared/api/errors";

/**
 * S-9 질의응답 목록.
 *
 * <p><b>상태 뱃지가 없다.</b> 답변 완료·미답변을 만들지 않기로 확정했다 —
 * 강사가 1명이라 200명분 상태를 닫는 절차가 그대로 병목이 된다.
 *
 * <p>공개 여부의 기본값은 <b>비공개</b>다. 기본이 공개면 학생이 성적이나 개인 사정을
 * 무심코 같은 반 20명 앞에 쓰게 된다.
 */
export default function StudentQnaPage() {
  const queryClient = useQueryClient();
  const [writing, setWriting] = useState(false);
  const [reviewing, setReviewing] = useState(false);

  const { data: page, isLoading } = useQuery({
    queryKey: ["student", "qna"],
    queryFn: () => fetchQnaList({}),
  });

  /**
   * 내 후기가 있으면 「내 후기」, 없으면 「후기 쓰기」다.
   * 학생당 하나라 목록이 없다 — 다른 학생 후기는 보이지 않는다.
   */
  const { data: myReview } = useQuery({
    queryKey: ["student", "review"],
    queryFn: fetchMyReview,
  });

  /*
   * 글쓰기용 반 목록은 별도 엔드포인트를 두지 않는다. S-7의 getMe()가 이미
   * 재원 중인 반 전체(글이 없는 반 포함)를 {classRoomId, name}[]로 내려주고,
   * 다른 학생 화면(StudentScorePage)도 같은 쿼리키("student","me")를 쓰므로
   * 여기서도 재사용해야 캐시가 한 벌로 유지되고 중복 요청이 나지 않는다.
   */
  const { data: me } = useQuery({
    queryKey: ["student", "me"],
    queryFn: getMe,
  });
  const classRooms = me?.classRooms;

  return (
    <div className="space-y-4">
      <PageTitle
        action={
          <div className="flex shrink-0 gap-2">
            <button
              type="button"
              onClick={() => setReviewing((v) => !v)}
              className="shrink-0 rounded-lg border border-brand-200 bg-brand-50 px-3 py-1.5
                         text-sm font-medium text-brand-700"
            >
              {myReview ? "내 후기" : "후기 쓰기"}
            </button>
            <button
              type="button"
              onClick={() => setWriting((v) => !v)}
              className="shrink-0 rounded-lg bg-brand-900 px-3 py-1.5 text-sm font-medium text-white"
            >
              {writing ? "닫기" : "질문하기"}
            </button>
          </div>
        }
      >
        질의응답
      </PageTitle>

      {reviewing && myReview !== undefined && (
        <ReviewForm
          myReview={myReview}
          onDone={() => {
            setReviewing(false);
            queryClient.invalidateQueries({ queryKey: ["student", "review"] });
          }}
        />
      )}

      {writing && classRooms && (
        <QuestionForm
          classRooms={classRooms}
          onDone={() => {
            setWriting(false);
            queryClient.invalidateQueries({ queryKey: ["student", "qna"] });
          }}
        />
      )}

      {isLoading && <p className="text-sm text-brand-500">불러오는 중…</p>}

      {page?.items.length === 0 && (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-brand-500">
          아직 질문이 없습니다. 궁금한 걸 물어보세요.
        </p>
      )}

      <ul className="space-y-2">
        {page?.items.map((item) => (
          <li key={item.postId}>
            <Link
              to={`/student/qna/${item.postId}`}
              className="block rounded-xl bg-white p-4 ring-1 ring-brand-100"
            >
              <div className="flex items-start justify-between gap-2">
                <span className="min-w-0 flex-1 truncate font-medium text-brand-900">
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
    </div>
  );
}

function QuestionForm({
  classRooms,
  onDone,
}: {
  classRooms: { classRoomId: number; name: string }[];
  onDone: () => void;
}) {
  const [classRoomId, setClassRoomId] = useState(classRooms[0]?.classRoomId ?? 0);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [isPublic, setIsPublic] = useState(false);
  const [s3Keys, setS3Keys] = useState<string[]>([]);

  const mutation = useMutation({
    mutationFn: () => createQna({ classRoomId, title, content, isPublic, s3Keys }),
    onSuccess: onDone,
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        mutation.mutate();
      }}
      className="space-y-3 rounded-xl bg-white p-4 ring-1 ring-brand-100"
    >
      {/* 반이 하나뿐인 학생에게는 고를 것이 없으므로 아예 안 보인다 */}
      {classRooms.length > 1 && (
        <select
          value={classRoomId}
          onChange={(e) => setClassRoomId(Number(e.target.value))}
          className="w-full rounded-lg border border-brand-200 p-2 text-sm"
        >
          {classRooms.map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>
      )}

      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제목"
        maxLength={200}
        required
        className="w-full rounded-lg border border-brand-200 p-2 text-sm"
      />

      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="궁금한 내용을 적어 주세요."
        rows={5}
        required
        className="w-full rounded-lg border border-brand-200 p-2 text-sm"
      />

      <PhotoPicker role="student" value={s3Keys} onChange={setS3Keys} />

      <label className="flex items-center gap-2 text-sm text-brand-700">
        <input
          type="checkbox"
          checked={isPublic}
          onChange={(e) => setIsPublic(e.target.checked)}
        />
        같은 반 학생들에게도 공개
      </label>
      <p className="text-xs text-brand-500">
        공개하지 않으면 선생님만 볼 수 있습니다.
      </p>

      <button
        type="submit"
        disabled={mutation.isPending}
        className="w-full rounded-lg bg-brand-900 py-2.5 text-sm font-medium text-white
                   disabled:opacity-50"
      >
        {mutation.isPending ? "올리는 중…" : "질문 올리기"}
      </button>
      {mutation.isError && (
        <p className="text-xs text-red-600">올리지 못했습니다. 다시 시도해 주세요.</p>
      )}
    </form>
  );
}

/**
 * 수강 후기 폼. myReview가 있으면 그 값으로 채워진 수정 모드이고 「삭제」가 함께 뜬다 —
 * 두 번 쓰려 해도 새 후기를 만드는 길은 없다. 학생당 하나다.
 */
function ReviewForm({
  myReview,
  onDone,
}: {
  myReview: MyReview | null;
  onDone: () => void;
}) {
  const [rating, setRating] = useState(myReview?.rating ?? 0);
  const [content, setContent] = useState(myReview?.content ?? "");
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      myReview ? updateReview({ rating, content }) : createReview({ rating, content }),
    onSuccess: onDone,
    onError: (e) => setError(errorMessage(e, "저장하지 못했습니다.")),
  });

  const remove = useMutation({
    mutationFn: deleteReview,
    onSuccess: onDone,
    onError: (e) => setError(errorMessage(e, "삭제하지 못했습니다.")),
  });

  // 서버도 같은 조건으로 400을 내지만, 그 전에 화면에서 먼저 막고 이유를 보여준다
  const ratingMissing = rating < 0.5;
  const contentMissing = content.trim().length === 0;
  const missing = ratingMissing && contentMissing
    ? "별점과 내용을 모두 입력해 주세요."
    : ratingMissing
      ? "별점을 선택해 주세요."
      : contentMissing
        ? "내용을 적어 주세요."
        : null;

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (missing) return;
        save.mutate();
      }}
      className="space-y-3 rounded-xl bg-white p-4 ring-1 ring-brand-100"
    >
      <StarRating value={rating} onChange={setRating} />

      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="수업은 어떠셨나요?"
        rows={5}
        className="w-full rounded-lg border border-brand-200 p-2 text-sm"
      />

      {missing && <p className="text-xs text-red-600">{missing}</p>}
      {error && <p className="text-xs text-red-600">{error}</p>}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={save.isPending || !!missing}
          className="flex-1 rounded-lg bg-brand-900 py-2.5 text-sm font-medium text-white
                     disabled:opacity-50"
        >
          {save.isPending ? "저장하는 중…" : "저장"}
        </button>
        {myReview && (
          <button
            type="button"
            onClick={() => {
              if (confirm("삭제하면 되돌릴 수 없습니다. 삭제할까요?")) remove.mutate();
            }}
            disabled={remove.isPending}
            className="shrink-0 rounded-lg border border-red-200 px-4 py-2.5 text-sm font-medium
                       text-red-600 disabled:opacity-50"
          >
            삭제
          </button>
        )}
      </div>
    </form>
  );
}
