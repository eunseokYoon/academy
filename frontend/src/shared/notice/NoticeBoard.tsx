import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Badge } from "../components/Badge";
import { Modal } from "../components/Modal";
import { getNotice, listNotices } from "./api";

/**
 * 학생·학부모 공용 공지 목록. 학부모는 studentId를 넘겨 자녀 기준으로 본다.
 *
 * <p><b>본문을 HTML로 렌더링하지 않는다.</b> 사용자가 입력한 텍스트라
 * dangerouslySetInnerHTML이나 마크다운 렌더러를 붙이면 XSS 처리가 필요해진다.
 * whitespace-pre-wrap으로 줄바꿈만 유지하면 충분하다.
 *
 * <p>읽음 표시는 없다(1차 범위 밖). 읽은 공지도 계속 같은 모양으로 남는다.
 */
export function NoticeBoard({ studentId }: { studentId?: number }) {
  const [openId, setOpenId] = useState<number | null>(null);

  const notices = useQuery({
    queryKey: ["notices", studentId ?? "me"],
    queryFn: () => listNotices(studentId ? { studentId } : {}),
    enabled: studentId !== undefined ? studentId !== null : true,
  });

  const detail = useQuery({
    queryKey: ["notices", "detail", openId, studentId ?? "me"],
    queryFn: () => getNotice(openId!, studentId),
    enabled: openId !== null,
  });

  if (notices.isPending) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }

  const items = notices.data?.items ?? [];

  return (
    <div className="space-y-3">
      <h2 className="text-lg font-semibold text-slate-900">학원 공지 · 안내</h2>

      {items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          등록된 공지가 없습니다.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm">
          {items.map((notice) => (
            <li key={notice.noticeId}>
              <button
                type="button"
                onClick={() => setOpenId(notice.noticeId)}
                className="flex w-full items-start justify-between gap-2 px-3 py-3 text-left"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    {notice.pinned && <Badge tone="warn">고정</Badge>}
                    <span className="truncate text-sm font-medium text-slate-900">
                      {notice.title}
                    </span>
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    {notice.publishedAt.slice(5, 10).replace("-", "/")}
                  </span>
                </span>
                <span className="shrink-0 text-slate-300">›</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {openId !== null && (
        <Modal title={detail.data?.title ?? "공지"} onClose={() => setOpenId(null)}>
          {detail.isPending || !detail.data ? (
            <p className="text-sm text-slate-400">불러오는 중…</p>
          ) : (
            <div className="space-y-2">
              <p className="text-xs text-slate-500">
                {detail.data.publishedAt.slice(0, 10).replace(/-/g, ".")}
              </p>
              {/* 일반 텍스트 + 줄바꿈만. 마크다운·HTML 렌더링 금지 */}
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                {detail.data.content}
              </p>
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}
