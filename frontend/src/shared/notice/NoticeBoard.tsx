import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { errorMessage } from "../api/errors";
import { FormError } from "../components/FormError";
import { Modal } from "../components/Modal";
import { PageTitle, SectionHead, TintBlock } from "../components/Section";
import { formatBytes } from "../material/types";
import { fetchAttachmentDownloadUrl, getNotice, listNotices } from "./api";

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

  /**
   * 다운로드 URL은 5분짜리다. 미리 받아 두지 말고 누를 때 받는다 —
   * 상세를 열어 두고 한참 뒤에 누르면 만료된 URL을 쓰게 된다.
   *
   * <p>새 탭 요청(window.open)은 URL을 fetch로 받아온 <b>뒤</b>에 일어나서 클릭의
   * 사용자 제스처와 묶이지 않을 수 있다 — 그러면 브라우저가 팝업을 조용히 막는다.
   * 이때 fetch 자체는 성공했으므로 isError는 그대로 false라 "받기"를 눌러도 아무
   * 일도 안 일어난 것처럼 보인다. 사파리·카카오톡 인앱 브라우저가 특히 엄격하다.
   * window.open이 null을 돌려주면(차단됨) 같은 탭 이동으로 떨어뜨려
   * StudentMaterialPage.tsx의 다운로드와 같은 방식으로 사용자가 파일을 받게 한다.
   */
  const download = useMutation({
    mutationFn: (attachmentId: number) =>
      fetchAttachmentDownloadUrl(openId!, attachmentId, studentId),
    onSuccess: (data) => {
      const win = window.open(data.downloadUrl, "_blank", "noopener");
      if (!win) {
        window.location.href = data.downloadUrl;
      }
    },
  });

  function openNotice(noticeId: number) {
    setOpenId(noticeId);
    download.reset();
  }

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
                onClick={() => openNotice(notice.noticeId)}
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
                    {/* QnaListPage의 hasPhoto(· 사진)와 같은 언어 — 목록에서부터 자료 유무를 알린다 */}
                    {notice.hasAttachment && (
                      <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[9.5px]
                                       font-bold text-slate-600">
                        📎 자료
                      </span>
                    )}
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
        <Modal
          title={detail.data?.title ?? "공지"}
          onClose={() => {
            setOpenId(null);
            download.reset();
          }}
        >
          {detail.isPending || !detail.data ? (
            <p className="text-sm text-slate-400">불러오는 중…</p>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-slate-500">
                {detail.data.publishedAt.slice(0, 10).replace(/-/g, ".")}
              </p>
              {/* 일반 텍스트 + 줄바꿈만. 마크다운·HTML 렌더링 금지 */}
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                {detail.data.content}
              </p>

              {detail.data.attachments.length > 0 && (
                <div className="space-y-1.5 border-t border-slate-100 pt-3">
                  {download.isError && <FormError message={errorMessage(download.error)} />}
                  {detail.data.attachments.map((attachment) => (
                    <button
                      key={attachment.attachmentId}
                      type="button"
                      onClick={() => download.mutate(attachment.attachmentId)}
                      disabled={download.isPending}
                      className="flex w-full items-center justify-between gap-2 rounded-lg
                                 bg-slate-50 px-3 py-2 text-left transition-colors
                                 active:bg-slate-100 disabled:opacity-60"
                    >
                      <span className="min-w-0 flex-1 truncate text-[13px] text-slate-700">
                        📎 {attachment.fileName}
                        {attachment.bytes !== null && (
                          <span className="tnum text-slate-400">
                            {" "}
                            ({formatBytes(attachment.bytes)})
                          </span>
                        )}
                      </span>
                      <span className="shrink-0 rounded-lg border border-brand-200 bg-white
                                       px-2 py-1 text-[11px] font-bold text-brand-700">
                        받기
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}
