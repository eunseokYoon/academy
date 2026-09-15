import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { answerTeacherQna, deleteTeacherQna, fetchTeacherQnaDetail } from "../api";
import { PhotoPicker } from "../../../shared/qna/PhotoPicker";
import { PhotoStrip } from "../../../shared/qna/PhotoStrip";

/**
 * T-15 상세. 답글을 쓰고, 부적절한 글·답글을 내린다.
 *
 * <p>질문 삭제는 답글과 사진까지 함께 지운다(ON DELETE CASCADE). 되돌릴 수 없다.
 */
export default function QnaDetailPage() {
  const { postId } = useParams();
  const id = Number(postId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [content, setContent] = useState("");
  const [s3Keys, setS3Keys] = useState<string[]>([]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["teacher", "qna", "detail", id],
    queryFn: () => fetchTeacherQnaDetail(id),
  });

  const answer = useMutation({
    mutationFn: () => answerTeacherQna(id, { content, s3Keys }),
    onSuccess: () => {
      setContent("");
      setS3Keys([]);
      queryClient.invalidateQueries({ queryKey: ["teacher", "qna", "detail", id] });
    },
  });

  const removePost = useMutation({
    mutationFn: (targetId: number) => deleteTeacherQna(targetId),
    onSuccess: (_, targetId) => {
      if (targetId === id) {
        queryClient.invalidateQueries({ queryKey: ["teacher", "qna"] });
        navigate("/teacher/qna");
        return;
      }
      queryClient.invalidateQueries({ queryKey: ["teacher", "qna", "detail", id] });
    },
  });

  if (isLoading) return <p className="text-sm text-brand-500">불러오는 중…</p>;
  if (isError || !data) return <p className="text-sm text-brand-500">없는 글입니다.</p>;

  return (
    <div className="max-w-screen-sm space-y-4">
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
        <button
          type="button"
          onClick={() => {
            if (confirm("질문을 지우면 답글과 사진도 함께 사라집니다. 지울까요?")) {
              removePost.mutate(id);
            }
          }}
          className="mt-3 text-xs text-red-600"
        >
          질문 삭제
        </button>
      </article>

      <ul className="space-y-2">
        {data.answers.map((item) => (
          <li
            key={item.answerId}
            className={`rounded-xl p-4 ring-1 ring-brand-100 ${
              item.byTeacher ? "bg-brand-50" : "bg-white"
            }`}
          >
            <p className="text-xs font-medium text-brand-700">{item.authorName}</p>
            <p className="mt-1 whitespace-pre-wrap text-sm text-brand-800">{item.content}</p>
            <PhotoStrip photos={item.photos} />
            <button
              type="button"
              onClick={() => {
                if (confirm("이 답글을 지울까요?")) removePost.mutate(item.answerId);
              }}
              className="mt-2 text-xs text-red-600"
            >
              답글 삭제
            </button>
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
          placeholder="답변을 적어 주세요."
          rows={4}
          required
          className="w-full rounded-lg border border-brand-200 p-2 text-sm"
        />
        <PhotoPicker role="teacher" value={s3Keys} onChange={setS3Keys} />
        <button
          type="submit"
          disabled={answer.isPending}
          className="w-full rounded-lg bg-brand-900 py-2.5 text-sm font-medium text-white
                     disabled:opacity-50"
        >
          {answer.isPending ? "올리는 중…" : "답변 쓰기"}
        </button>
      </form>
    </div>
  );
}
