import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { answerQna, deleteQna, fetchQnaDetail } from "./api";
import { PhotoPicker } from "../../shared/qna/PhotoPicker";
import { PhotoStrip } from "../../shared/qna/PhotoStrip";

/**
 * S-9 상세. 질문 하나와 그 아래 답글들이다.
 *
 * <p>답글에는 차례가 없다. 학생도 선생님도 아무 때나 여러 번 쓴다 —
 * "선생님 차례"를 계산해 화면을 잠그지 마라(확정).
 */
export default function StudentQnaDetailPage() {
  const { postId } = useParams();
  const id = Number(postId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [content, setContent] = useState("");
  const [s3Keys, setS3Keys] = useState<string[]>([]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["student", "qna", id],
    queryFn: () => fetchQnaDetail(id),
  });

  const answer = useMutation({
    mutationFn: () => answerQna(id, { content, s3Keys }),
    onSuccess: () => {
      setContent("");
      setS3Keys([]);
      queryClient.invalidateQueries({ queryKey: ["student", "qna", id] });
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteQna(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["student", "qna"] });
      navigate("/student/qna");
    },
  });

  if (isLoading) return <p className="text-sm text-brand-500">불러오는 중…</p>;
  if (isError || !data) {
    return <p className="text-sm text-brand-500">볼 수 없는 글입니다.</p>;
  }

  return (
    <div className="space-y-4">
      <article className="rounded-xl bg-white p-4 ring-1 ring-brand-100">
        <h1 className="font-bold text-brand-900">
          {!data.isPublic && <span aria-label="비공개">🔒 </span>}
          {data.title}
        </h1>
        <p className="mt-1 text-xs text-brand-500">
          {data.authorName} · {data.classRoomName}
        </p>
        {/* 사용자 입력이다. HTML로 렌더링하지 마라 */}
        <p className="mt-3 whitespace-pre-wrap text-sm text-brand-800">{data.content}</p>
        <PhotoStrip photos={data.photos} />

        {data.editable && (
          <button
            type="button"
            onClick={() => {
              if (confirm("질문을 지우면 답글도 함께 사라집니다. 지울까요?")) remove.mutate();
            }}
            className="mt-3 text-xs text-red-600"
          >
            질문 삭제
          </button>
        )}
      </article>

      <ul className="space-y-2">
        {data.answers.map((item) => (
          <li
            key={item.answerId}
            className={`rounded-xl p-4 ring-1 ${
              item.byTeacher
                ? "bg-brand-50 ring-brand-100"
                : "bg-white ring-brand-100"
            }`}
          >
            <p className="text-xs font-medium text-brand-700">{item.authorName}</p>
            <p className="mt-1 whitespace-pre-wrap text-sm text-brand-800">{item.content}</p>
            <PhotoStrip photos={item.photos} />
          </li>
        ))}
      </ul>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          answer.mutate();
        }}
        className="space-y-3 rounded-xl bg-white p-4 ring-1 ring-brand-100"
      >
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="답글을 남겨 보세요."
          rows={3}
          required
          className="w-full rounded-lg border border-brand-200 p-2 text-sm"
        />
        <PhotoPicker role="student" value={s3Keys} onChange={setS3Keys} />
        <button
          type="submit"
          disabled={answer.isPending}
          className="w-full rounded-lg bg-brand-900 py-2.5 text-sm font-medium text-white
                     disabled:opacity-50"
        >
          {answer.isPending ? "올리는 중…" : "답글 쓰기"}
        </button>
      </form>
    </div>
  );
}
