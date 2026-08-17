import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { fetchTeacherQnaList, listClassRooms } from "../api";

/**
 * T-15 질의응답 목록. 비공개 질문도 전부 나온다.
 *
 * <p><b>미답변 개수나 상태 뱃지를 만들지 마라.</b> 확인이라는 행위를 두지 않기로 확정했다 —
 * 숙제 피드백·확인 단계를 V12에서 없앤 것과 같은 이유다.
 */
export default function QnaListPage() {
  const [classRoomId, setClassRoomId] = useState<number | undefined>(undefined);

  const { data: classRooms } = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const { data: page, isLoading } = useQuery({
    queryKey: ["teacher", "qna", classRoomId],
    queryFn: () => fetchTeacherQnaList({ classRoomId }),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-lg font-bold text-brand-900">질의응답</h1>
        <select
          value={classRoomId ?? ""}
          onChange={(e) =>
            setClassRoomId(e.target.value === "" ? undefined : Number(e.target.value))
          }
          className="rounded-lg border border-brand-200 p-2 text-sm"
        >
          <option value="">전체 반</option>
          {classRooms?.map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>
      </div>

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
