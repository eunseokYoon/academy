import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Modal } from "../components/Modal";
import { PageTitle, SectionHead, TintBlock } from "../components/Section";
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
    return (
      <>
        <PageTitle>학원 공지 · 안내</PageTitle>
        <p className="text-sm text-slate-400">불러오는 중…</p>
      </>
    );
  }

  const items = notices.data?.items ?? [];

  return (
    <div>
      <PageTitle>학원 공지 · 안내</PageTitle>

      {items.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">등록된 공지가 없습니다.</p>
        </TintBlock>
      ) : (
        <>
          <SectionHead tone="brand" title="전체 공지" count={items.length} />
          <TintBlock tone="brand">
            {items.map((notice) => (
              <button
                key={notice.noticeId}
                type="button"
                onClick={() => setOpenId(notice.noticeId)}
                className="flex w-full items-center justify-between gap-2 px-3.5 py-3 text-left
                           transition-colors active:bg-brand-100/60"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    {/* 옅은 배지는 남색 면 위에서 배경과 붙는다. 채운 남색으로 뒤집는다 */}
                    {notice.pinned && (
                      <span className="shrink-0 rounded bg-brand-600 px-1.5 py-0.5 text-[9.5px]
                                       font-bold text-white">
                        고정
                      </span>
                    )}
                    <span className="truncate text-[14px] font-semibold text-brand-950">
                      {notice.title}
                    </span>
                  </span>
                  <span className="tnum mt-0.5 block text-[11.5px] text-brand-600/70">
                    {notice.publishedAt.slice(5, 10).replace("-", "/")}
                  </span>
                </span>
                <span aria-hidden="true" className="shrink-0 text-brand-300">
                  ›
                </span>
              </button>
            ))}
          </TintBlock>
        </>
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
